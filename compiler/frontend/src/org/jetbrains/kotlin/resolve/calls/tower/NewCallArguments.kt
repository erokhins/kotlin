/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.resolve.calls.tower

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.BaseResolvedCall
import org.jetbrains.kotlin.resolve.calls.callUtil.getCall
import org.jetbrains.kotlin.resolve.calls.context.BasicCallResolutionContext
import org.jetbrains.kotlin.resolve.calls.context.ResolutionContext
import org.jetbrains.kotlin.resolve.calls.inference.model.ConstraintStorage
import org.jetbrains.kotlin.resolve.calls.model.*
import org.jetbrains.kotlin.resolve.calls.model.LambdaArgument
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.resolve.scopes.receivers.ExpressionReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValueWithSmartCastInfo
import org.jetbrains.kotlin.resolve.scopes.receivers.TransientReceiver
import org.jetbrains.kotlin.types.UnwrappedType
import org.jetbrains.kotlin.types.checker.intersectWrappedTypes
import org.jetbrains.kotlin.types.expressions.KotlinTypeInfo

class SimpleTypeArgumentImpl(
        val typeReference: KtTypeReference,
        override val type: UnwrappedType
): SimpleTypeArgument

// all arguments should be inherited from this class.
// But receivers is not, because for them there is no corresponding valueArgument
abstract class PSICallArgument: CallArgument {
    abstract val valueArgument: ValueArgument
    abstract val dataFlowInfoBeforeThisArgument: DataFlowInfo
    abstract val dataFlowInfoAfterThisArgument: DataFlowInfo

    override fun toString() = valueArgument.getArgumentExpression()?.text?.replace('\n', ' ') ?: valueArgument.toString()
}

val CallArgument.psiCallArgument: PSICallArgument get() {
    assert(this is PSICallArgument) {
        "Incorrect CallArgument: $this. Java class: ${javaClass.canonicalName}"
    }
    return this as PSICallArgument
}

val CallArgument.psiExpression: KtExpression? get() {
    if (this is ReceiverExpressionArgument) {
        return (receiver.receiverValue as? ExpressionReceiver)?.expression
    }
    return psiCallArgument.valueArgument.getArgumentExpression()
}

class ParseErrorArgument(
        override val valueArgument: ValueArgument,
        override val dataFlowInfoAfterThisArgument: DataFlowInfo,
        builtIns: KotlinBuiltIns
): ExpressionArgument, PSICallArgument() {
    override val type: UnwrappedType = builtIns.nothingType
    override val receiver = ReceiverValueWithSmartCastInfo(TransientReceiver(type), emptySet(), isStable = true)

    override val unstableType: UnwrappedType? get() = null
    override val isSafeCall: Boolean get() = false

    override val isSpread: Boolean get() = valueArgument.getSpreadElement() != null
    override val argumentName: Name? get() = valueArgument.getArgumentName()?.asName

    override val dataFlowInfoBeforeThisArgument: DataFlowInfo
        get() = dataFlowInfoAfterThisArgument
}

class LambdaArgumentIml(
        val outerCallContext: BasicCallResolutionContext,
        override val valueArgument: ValueArgument,
        override val dataFlowInfoBeforeThisArgument: DataFlowInfo,
        val ktLambdaExpression: KtLambdaExpression,
        override val argumentName: Name?,
        override val parametersTypes: Array<UnwrappedType?>?
) : LambdaArgument, PSICallArgument() {
    override val dataFlowInfoAfterThisArgument: DataFlowInfo
        get() = dataFlowInfoBeforeThisArgument
}

class FunctionExpressionImpl(
        val outerCallContext: BasicCallResolutionContext,
        override val valueArgument: ValueArgument,
        override val dataFlowInfoBeforeThisArgument: DataFlowInfo,
        val ktFunction: KtNamedFunction,
        override val argumentName: Name?,
        override val receiverType: UnwrappedType?,
        override val parametersTypes: Array<UnwrappedType?>,
        override val returnType: UnwrappedType?
) : FunctionExpression, PSICallArgument() {
    override val dataFlowInfoAfterThisArgument: DataFlowInfo
        get() = dataFlowInfoBeforeThisArgument
}

class CallableReferenceArgumentImpl(
        override val valueArgument: ValueArgument,
        override val dataFlowInfoBeforeThisArgument: DataFlowInfo,
        override val dataFlowInfoAfterThisArgument: DataFlowInfo,
        val ktCallableReferenceExpression: KtCallableReferenceExpression,
        override val argumentName: Name?,
        override val lhsType: UnwrappedType?,
        override val constraintStorage: ConstraintStorage
) : CallableReferenceArgument, PSICallArgument()

class SubCallArgumentImpl(
        override val valueArgument: ValueArgument,
        override val dataFlowInfoBeforeThisArgument: DataFlowInfo,
        override val dataFlowInfoAfterThisArgument: DataFlowInfo,
        override val receiver: ReceiverValueWithSmartCastInfo,
        override val resolvedCall: BaseResolvedCall.OnlyResolvedCall
): PSICallArgument(), SubCallArgument {
    override val isSpread: Boolean get() = valueArgument.getSpreadElement() != null
    override val argumentName: Name? get() = valueArgument.getArgumentName()?.asName
    override val isSafeCall: Boolean get() = false
}

class ExpressionArgumentImpl(
        override val valueArgument: ValueArgument,
        override val dataFlowInfoBeforeThisArgument: DataFlowInfo,
        override val dataFlowInfoAfterThisArgument: DataFlowInfo,
        override val receiver: ReceiverValueWithSmartCastInfo
): PSICallArgument(), ExpressionArgument {
    override val isSpread: Boolean get() = valueArgument.getSpreadElement() != null
    override val argumentName: Name? get() = valueArgument.getArgumentName()?.asName
    override val isSafeCall: Boolean get() = false

    override val type: UnwrappedType
    override val unstableType: UnwrappedType?

    init {
        val collectedType = intersectWrappedTypes(receiver.possibleTypes + receiver.receiverValue.type)
        if (receiver.isStable) {
            unstableType = null
            type = collectedType
        }
        else {
            unstableType = collectedType
            type = receiver.receiverValue.type.unwrap()
        }
    }
}

internal fun createSimplePSICallArgument(
        context: ResolutionContext<*>,
        valueArgument: ValueArgument,
        typeInfo: KotlinTypeInfo
): PSICallArgument? {
    val ktExpression = KtPsiUtil.getLastElementDeparenthesized(valueArgument.getArgumentExpression(), context.statementFilter) ?: return null
    val onlyResolvedCall = ktExpression.getCall(context.trace.bindingContext)?.let {
        context.trace.bindingContext.get(BindingContext.ONLY_RESOLVED_CALL, it)
    }
    val receiverToCast = context.transformToReceiverWithSmartCastInfo(
            ExpressionReceiver.create(ktExpression, typeInfo.type?.unwrap() ?: return null, context.trace.bindingContext)
    )

    return if (onlyResolvedCall == null) {
        ExpressionArgumentImpl(valueArgument, context.dataFlowInfo, typeInfo.dataFlowInfo, receiverToCast)
    }
    else {
        SubCallArgumentImpl(valueArgument, context.dataFlowInfo, typeInfo.dataFlowInfo, receiverToCast, onlyResolvedCall)
    }

}