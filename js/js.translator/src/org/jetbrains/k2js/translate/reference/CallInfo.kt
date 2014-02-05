/*
 * Copyright 2010-2013 JetBrains s.r.o.
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

package org.jetbrains.k2js.translate.reference

import com.google.dart.compiler.backend.js.ast.JsExpression
import org.jetbrains.jet.lang.resolve.calls.model.ResolvedCall
import org.jetbrains.jet.lang.descriptors.FunctionDescriptor
import org.jetbrains.jet.lang.descriptors.CallableDescriptor
import org.jetbrains.k2js.translate.context.TranslationContext
import org.jetbrains.jet.lang.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.k2js.translate.utils.JsDescriptorUtils.getDeclarationDescriptorForReceiver
import org.jetbrains.jet.lang.resolve.calls.tasks.ExplicitReceiverKind.*
import org.jetbrains.k2js.translate.utils.AnnotationsUtils
import org.jetbrains.jet.lang.psi.JetSuperExpression
import org.jetbrains.jet.lang.resolve.scopes.receivers.ExpressionReceiver
import org.jetbrains.jet.lang.descriptors.PropertyDescriptor
import org.jetbrains.jet.lang.descriptors.VariableDescriptor
import com.google.dart.compiler.backend.js.ast.JsName
import org.jetbrains.jet.lang.diagnostics.DiagnosticUtils


trait CallInfo {
    val context: TranslationContext
    val resolvedCall: ResolvedCall<out CallableDescriptor>

    val thisObject: JsExpression?
    val receiverObject: JsExpression?

    fun toString(): String {
        val location = DiagnosticUtils.atLocation(context.bindingContext(), callableDescriptor)
        val name = callableDescriptor.getName().asString()
        return "callableDescriptor: $name at $location; thisObject: $thisObject; receiverObject: $receiverObject"
    }
}

// if value == null, it is get access
class VariableAccessInfo(callInfo: CallInfo, val value: JsExpression? = null): CallInfo by callInfo

class FunctionCallInfo(callInfo: CallInfo, val argumentsInfo: CallArgumentTranslator.ArgumentsInfo) : CallInfo by callInfo

/**
 * no receivers - thisObjectOrReceiverObject = null,     receiverObject = null
 * this -         thisObjectOrReceiverObject = this,     receiverObject = null
 * receiver -     thisObjectOrReceiverObject = receiver, receiverObject = null
 * both -         thisObjectOrReceiverObject = this,     receiverObject = receiver
 */
class ExplicitReceivers(val receiverOrThisObject: JsExpression?, val receiverObject: JsExpression? = null)

fun TranslationContext.getCallInfo(resolvedCall: ResolvedCall<out CallableDescriptor>, receiver: JsExpression?): CallInfo {
    return createCallInfo(resolvedCall, ExplicitReceivers(receiver))
}

fun TranslationContext.getCallInfo(resolvedCall: ResolvedCall<out FunctionDescriptor>, receiver: JsExpression?): FunctionCallInfo {
    return getCallInfo(resolvedCall, ExplicitReceivers(receiver));
}

// two receiver need only for FunctionCall in VariableAsFunctionResolvedCall
fun TranslationContext.getCallInfo(resolvedCall: ResolvedCall<out FunctionDescriptor>, explicitReceivers: ExplicitReceivers): FunctionCallInfo {
    val callInfo = createCallInfo(resolvedCall, explicitReceivers)
    val argumentsInfo = CallArgumentTranslator.translate(resolvedCall, explicitReceivers.receiverOrThisObject, this)
    return FunctionCallInfo(callInfo, argumentsInfo)
}


val CallInfo.callableDescriptor: CallableDescriptor
    get() {
        return resolvedCall.getResultingDescriptor().getOriginal()
    }
fun CallInfo.isExtension(): Boolean {
    return receiverObject != null
}
fun CallInfo.isMemberCall(): Boolean {
    return thisObject != null
}
fun CallInfo.isNative(): Boolean {
    return AnnotationsUtils.isNativeObject(callableDescriptor)
}
fun CallInfo.isSuperInvocation() : Boolean {
    val thisObject = resolvedCall.getThisObject()
    return thisObject is ExpressionReceiver && ((thisObject as ExpressionReceiver)).getExpression() is JetSuperExpression
}
fun CallInfo.constructSafeCallIsNeeded(result: JsExpression): JsExpression {
    if (!resolvedCall.isSafeCall())
        return result

    val nullableReceiverForSafeCall = when (resolvedCall.getExplicitReceiverKind()) {
        BOTH_RECEIVERS, RECEIVER_ARGUMENT -> receiverObject
        else -> thisObject
    }
    return CallType.SAFE.constructCall(nullableReceiverForSafeCall, object : CallType.CallConstructor {
        override fun construct(receiver: JsExpression?): JsExpression {
            return result
        }
    }, context)
}

val VariableAccessInfo.variableDescriptor: VariableDescriptor
    get() {
        return callableDescriptor as VariableDescriptor
    }
val VariableAccessInfo.variableName : JsName
    get() {
        return context.getNameForDescriptor(variableDescriptor)
    }
fun VariableAccessInfo.isGetAccess(): Boolean {
    return value == null
}

val FunctionCallInfo.functionName : JsName
    get() {
        return context.getNameForDescriptor(callableDescriptor)
    }
fun FunctionCallInfo.hasSpreadOperator() : Boolean {
    return argumentsInfo.isHasSpreadOperator()
}


private fun TranslationContext.getThisObject(receiverValue: ReceiverValue): JsExpression {
    assert(receiverValue.exists(), "receiverValue must be exist here")
    return getThisObject(getDeclarationDescriptorForReceiver(receiverValue))
}

private fun TranslationContext.createCallInfo(resolvedCall: ResolvedCall<out CallableDescriptor>, explicitReceivers: ExplicitReceivers): CallInfo {
    val receiverKind = resolvedCall.getExplicitReceiverKind()

    fun getThisObject(): JsExpression? {
        val receiverValue = resolvedCall.getThisObject()
        if (!receiverValue.exists()) {
            return null
        }
        return when (receiverKind) {
            THIS_OBJECT, BOTH_RECEIVERS -> explicitReceivers.receiverOrThisObject
            else -> this.getThisObject(receiverValue)
        }
    }

    fun getReceiverObject(): JsExpression? {
        val receiverValue = resolvedCall.getReceiverArgument()
        if (!receiverValue.exists()) {
            return null
        }
        return when (receiverKind) {
            RECEIVER_ARGUMENT -> explicitReceivers.receiverOrThisObject
            BOTH_RECEIVERS -> explicitReceivers.receiverObject
            else -> this.getThisObject(receiverValue)
        }
    }

    fun getNullableReceiverForSafeCall(): JsExpression? {
        if (!resolvedCall.isSafeCall()) {
            return null
        }
        return when (receiverKind) {
            BOTH_RECEIVERS -> explicitReceivers.receiverObject
            else -> explicitReceivers.receiverOrThisObject
        }
    }
    return object : CallInfo {
        override val context: TranslationContext = this@createCallInfo
        override val resolvedCall: ResolvedCall<out CallableDescriptor> = resolvedCall
        override val thisObject: JsExpression? = getThisObject()
        override val receiverObject: JsExpression? = getReceiverObject()
    };
}