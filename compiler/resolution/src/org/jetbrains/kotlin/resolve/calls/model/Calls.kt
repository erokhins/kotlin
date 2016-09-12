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

package org.jetbrains.kotlin.resolve.calls.model

import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.calls.ASTCallKind
import org.jetbrains.kotlin.resolve.calls.ArgumentsToParametersMapper
import org.jetbrains.kotlin.resolve.calls.BaseResolvedCall
import org.jetbrains.kotlin.resolve.calls.inference.LambdaNewTypeVariable
import org.jetbrains.kotlin.resolve.calls.inference.NewTypeVariable
import org.jetbrains.kotlin.resolve.calls.inference.ReadOnlyConstraintSystem
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind
import org.jetbrains.kotlin.resolve.calls.tower.CandidateWithBoundDispatchReceiver
import org.jetbrains.kotlin.resolve.calls.util.createFunctionType
import org.jetbrains.kotlin.resolve.scopes.receivers.DetailedReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.QualifierReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValueWithSmartCastInfo
import org.jetbrains.kotlin.types.SimpleType
import org.jetbrains.kotlin.types.TypeSubstitutor
import org.jetbrains.kotlin.types.UnwrappedType
import org.jetbrains.kotlin.types.checker.intersectWrappedTypes
import org.jetbrains.kotlin.types.typeUtil.builtIns
import org.jetbrains.kotlin.utils.addToStdlib.check
import java.lang.UnsupportedOperationException


/*sealed*/ interface CallArgument {
    val isSpread: Boolean
    val argumentName: Name?
}

/*sealed*/ interface ReceiverCallArgument {
    val receiver: DetailedReceiver
}

class QualifierReceiverCallArgument(override val receiver: QualifierReceiver): ReceiverCallArgument {
    override fun toString() = "$receiver"
}

/*sealed*/ interface SimpleCallArgument : CallArgument, ReceiverCallArgument {
    override val receiver: ReceiverValueWithSmartCastInfo

    val isSafeCall: Boolean
}

class FakeArgumentForCallableReference(
        val callableReference: ChosenCallableReferenceDescriptor,
        val index: Int
) : CallArgument {
    override val isSpread: Boolean get() = false
    override val argumentName: Name? get() = null
}

interface ExpressionArgument : SimpleCallArgument {
    val type: UnwrappedType // with all smart casts if stable

    val unstableType: UnwrappedType? // if expression is not stable and has smart casts, then we create this type
}

class ReceiverExpressionArgument(
        override val receiver: ReceiverValueWithSmartCastInfo,
        override val isSafeCall: Boolean = false
) : ExpressionArgument {
    override val isSpread: Boolean get() = false
    override val argumentName: Name? get() = null

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

    override fun toString() = "$receiver" + if(isSafeCall) "?" else ""
}

interface SubCallArgument : SimpleCallArgument {
    val resolvedCall: BaseResolvedCall.OnlyResolvedCall
}

interface LambdaArgument : CallArgument {
    override val isSpread: Boolean
        get() = false

    /**
     * parametersTypes == null means, that there is no declared arguments
     * null inside array means that this type is not declared explicitly
     */
    val parametersTypes: Array<UnwrappedType?>?
}

interface FunctionExpression : LambdaArgument {
    override val parametersTypes: Array<UnwrappedType?>

    // null means that there function can not have receiver
    val receiverType: UnwrappedType?

    // null means that return type is not declared, for fun(){ ... } returnType == Unit
    val returnType: UnwrappedType?
}

interface CallableReferenceArgument : CallArgument {
    override val isSpread: Boolean
        get() = false

    // Foo::bar lhsType = Foo. For a::bar where a is expression, this type is null
    val lhsType: UnwrappedType?

    val constraintSystem: ReadOnlyConstraintSystem
}

interface ChosenCallableReferenceDescriptor : CallableReferenceArgument {
    val candidate: CandidateWithBoundDispatchReceiver

    val extensionReceiver: ReceiverValue?
}

/*sealed*/ interface TypeArgument

// todo allow '_' in frontend
object TypeArgumentPlaceholder : TypeArgument

interface SimpleTypeArgument: TypeArgument {
    val type: UnwrappedType
}

interface ASTCall {
    val callKind: ASTCallKind

    val explicitReceiver: ReceiverCallArgument?

    // a.(foo)() -- (foo) is dispatchReceiverForInvoke
    val dispatchReceiverForInvokeExtension: SimpleCallArgument? get() = null

    val name: Name

    val typeArguments: List<TypeArgument>

    val argumentsInParenthesis: List<CallArgument>

    val externalArgument: CallArgument?
}

private fun SimpleCallArgument.checkReceiverInvariants() {
    assert(!isSpread) {
        "Receiver cannot be a spread: $this"
    }
    assert(argumentName == null) {
        "Argument name should be null for receiver: $this, but it is $argumentName"
    }
}

fun ASTCall.checkCallInvariants() {
    assert(explicitReceiver !is LambdaArgument && explicitReceiver !is CallableReferenceArgument) {
        "Lambda argument or callable reference is not allowed as explicit receiver: $explicitReceiver"
    }

    (explicitReceiver as? SimpleCallArgument)?.checkReceiverInvariants()
    dispatchReceiverForInvokeExtension?.checkReceiverInvariants()

    if (callKind != ASTCallKind.FUNCTION) {
        assert(externalArgument == null) {
            "External argument is not allowed not for function call: $externalArgument."
        }
        assert(argumentsInParenthesis.isEmpty()) {
            "Arguments in parenthesis should be empty for not function call: $this "
        }
        assert(dispatchReceiverForInvokeExtension == null) {
            "Dispatch receiver for invoke should be null for not function call: $dispatchReceiverForInvokeExtension"
        }
    }
    else {
        assert(externalArgument == null || !externalArgument!!.isSpread) {
            "External argument cannot nave spread element: $externalArgument"
        }

        assert(externalArgument?.argumentName == null) {
            "Illegal external argument with name: $externalArgument"
        }

        assert(dispatchReceiverForInvokeExtension == null || !dispatchReceiverForInvokeExtension!!.isSafeCall) {
            "Dispatch receiver for invoke cannot be safe: $dispatchReceiverForInvokeExtension"
        }
    }
}

//-----------------------------

interface NotCompletedResolvedCall {
    val constraintSystem: CommonConstrainSystem

//    fun complete(info: CompletingInfo): NewResolvedCall<*>
}

sealed class CompletingInfo {
    class Substitutor(val substitutor: TypeSubstitutor) : CompletingInfo()

    class ExpectedType(val expectedType: UnwrappedType?) : CompletingInfo()
}

interface CommonConstrainSystem {

}

sealed class ArgumentWithPostponeResolution {
    abstract val outerCall: ASTCall
    abstract val argument: CallArgument
    abstract val myTypeVariables: Collection<NewTypeVariable>
    abstract val inputType: Collection<UnwrappedType> // parameters and implicit receiver
    abstract val outputType: UnwrappedType?

    var analyzed: Boolean = false

}

class ResolvedLambdaArgument(
        override val outerCall: ASTCall,
        override val argument: LambdaArgument,
        override val myTypeVariables: Collection<LambdaNewTypeVariable>,
        val receiver: UnwrappedType?,
        val parameters: List<UnwrappedType>,
        val returnType: UnwrappedType
) : ArgumentWithPostponeResolution() {
    val type: SimpleType = createFunctionType(returnType.builtIns, Annotations.EMPTY, receiver, parameters, returnType) // todo support annotations

    override val inputType: Collection<UnwrappedType> get() = receiver?.let { parameters + it } ?: parameters
    override val outputType: UnwrappedType get() = returnType
}


class ResolvedPropertyReference(
        val outerCall: ASTCall,
        val argument: ChosenCallableReferenceDescriptor,
        val reflectionType: UnwrappedType
) {
    val boundDispatchReceiver: ReceiverValue? get() = argument.candidate.dispatchReceiver?.receiverValue?.check { it !is MockReceiverForCallableReference }
    val boundExtensionReceiver: ReceiverValue? get() = argument.extensionReceiver?.check { it !is MockReceiverForCallableReference }
}

class ResolvedFunctionReference(
        val outerCall: ASTCall,
        val argument: ChosenCallableReferenceDescriptor,
        val reflectionType: UnwrappedType,
        val argumentsMapping: ArgumentsToParametersMapper.ArgumentMapping?
) {
    val boundDispatchReceiver: ReceiverValue? get() = argument.candidate.dispatchReceiver?.receiverValue?.check { it !is MockReceiverForCallableReference }
    val boundExtensionReceiver: ReceiverValue? get() = argument.extensionReceiver?.check { it !is MockReceiverForCallableReference }
}


fun ASTCall.getExplicitDispatchReceiver(explicitReceiverKind: ExplicitReceiverKind) = when (explicitReceiverKind) {
    ExplicitReceiverKind.DISPATCH_RECEIVER -> explicitReceiver
    ExplicitReceiverKind.BOTH_RECEIVERS -> dispatchReceiverForInvokeExtension
    else -> null
}

fun ASTCall.getExplicitExtensionReceiver(explicitReceiverKind: ExplicitReceiverKind) = when (explicitReceiverKind) {
    ExplicitReceiverKind.EXTENSION_RECEIVER, ExplicitReceiverKind.BOTH_RECEIVERS -> explicitReceiver
    else -> null
}

object ThrowableASTCall : ASTCall {
    override val callKind: ASTCallKind get() = throw UnsupportedOperationException()
    override val explicitReceiver: ReceiverCallArgument? get() = throw UnsupportedOperationException()
    override val name: Name get() = throw UnsupportedOperationException()
    override val typeArguments: List<TypeArgument> get() = throw UnsupportedOperationException()
    override val argumentsInParenthesis: List<CallArgument> get() = throw UnsupportedOperationException()
    override val externalArgument: CallArgument? get() = throw UnsupportedOperationException()
}

class MockReceiverForCallableReference(val lhsOrDeclaredType: UnwrappedType) : ReceiverValue {
    override fun getType() = lhsOrDeclaredType
}

val ChosenCallableReferenceDescriptor.dispatchNotBoundReceiver : UnwrappedType?
    get() = (candidate.dispatchReceiver?.receiverValue as? MockReceiverForCallableReference)?.lhsOrDeclaredType

val ChosenCallableReferenceDescriptor.extensionNotBoundReceiver : UnwrappedType?
    get() = (extensionReceiver as? MockReceiverForCallableReference)?.lhsOrDeclaredType
