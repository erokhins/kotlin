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

package org.jetbrains.kotlin.resolve.calls

import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.resolve.calls.inference.*
import org.jetbrains.kotlin.resolve.calls.model.*
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind
import org.jetbrains.kotlin.resolve.calls.tower.ResolutionCandidateStatus
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValueWithSmartCastInfo
import org.jetbrains.kotlin.types.TypeSubstitutor
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.UnwrappedType
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.types.typeUtil.contains
import org.jetbrains.kotlin.utils.addToStdlib.check

interface LambdaAnalyzer {
    fun analyzeAndGetRelatedCalls(
            topLevelCall: ASTCall,
            lambdaArgument: LambdaArgument,
            receiverType: UnwrappedType?,
            parameters: List<UnwrappedType>,
            expectedReturnType: UnwrappedType? // null means, that return type is not proper i.e. it depends on some type variables
    ): List<BaseResolvedCall<*>>
}

sealed class CompletedCall<out D : CallableDescriptor> {
    abstract val lastCall: Simple<D>

    class Simple<out D : CallableDescriptor>(
            val astCall: ASTCall,
            val candidateDescriptor: D,
            val resultingDescriptor: D,
            val resolutionStatus: ResolutionCandidateStatus,
            val explicitReceiverKind: ExplicitReceiverKind,
            val dispatchReceiver: ReceiverValueWithSmartCastInfo?,
            val extensionReceiver: ReceiverValueWithSmartCastInfo?,
            val typeArguments: List<UnwrappedType>,
            val argumentMappingByOriginal: Map<ValueParameterDescriptor, ResolvedCallArgument>
    ): CompletedCall<D>() {
        override val lastCall: Simple<D> get() = this
    }

    class VariableAsFunction(
            val astCall: ASTCall,
            val variableCall: Simple<VariableDescriptor>,
            val invokeCall: Simple<FunctionDescriptor>
    ): CompletedCall<FunctionDescriptor>() {
        override val lastCall: Simple<FunctionDescriptor> get() = invokeCall
    }
}

sealed class BaseResolvedCall<out D : CallableDescriptor> {

    class CompletedResolvedCall<out D : CallableDescriptor>(
            val completedCall: CompletedCall<D>,
            val allInnerCalls: Collection<CompletedCall<*>>
    ): BaseResolvedCall<D>()

    class OnlyResolvedCall<out D : CallableDescriptor>(
            val candidate: NewResolutionCandidate<D>
    ) : BaseResolvedCall<D>() {
        val currentReturnType: UnwrappedType = candidate.lastCall.descriptorWithFreshTypes.returnTypeOrNothing
    }
}

class ASTCallCompleter(
        val constraintFixator: ConstraintFixator
) {

    fun <D : CallableDescriptor> transformWhenAmbiguity(candidate: NewResolutionCandidate<D>): BaseResolvedCall<D> =
            toCompletedBaseResolvedCall(candidate)

    fun <D : CallableDescriptor> completeCallIfNecessary(
            candidate: NewResolutionCandidate<D>,
            expectedType: UnwrappedType?,
            lambdaAnalyzer: LambdaAnalyzer
    ): BaseResolvedCall<D> {
        val topLevelCall =
                if (candidate is VariableAsFunctionResolutionCandidate) {
                    candidate.invokeCandidate
                }
                else {
                    candidate as SimpleResolutionCandidate
                }

        if (topLevelCall.prepareForCompletion(expectedType)) {
            topLevelCall.competeCall(lambdaAnalyzer)
            return toCompletedBaseResolvedCall(candidate)
        }

        return BaseResolvedCall.OnlyResolvedCall(candidate)
    }

    private fun <D : CallableDescriptor> toCompletedBaseResolvedCall(
            candidate: NewResolutionCandidate<D>
    ): BaseResolvedCall.CompletedResolvedCall<D> {
        val currentSubstitutor = candidate.lastCall.csBuilder.build().buildCurrentSubstitutor()
        val completedCall = candidate.toCompletedCall(currentSubstitutor)
        val competedCalls = candidate.lastCall.csBuilder.build().innerCalls.map {
            it.candidate.toCompletedCall(currentSubstitutor)
        }
        return BaseResolvedCall.CompletedResolvedCall(completedCall, competedCalls)
    }

    private fun <D : CallableDescriptor> NewResolutionCandidate<D>.toCompletedCall(substitutor: TypeSubstitutor): CompletedCall<D> {
        if (this is VariableAsFunctionResolutionCandidate) {
            val variable = resolvedVariable.toCompletedCall(substitutor)
            val invoke = invokeCandidate.toCompletedCall(substitutor)

            @Suppress("UNCHECKED_CAST")
            return CompletedCall.VariableAsFunction(astCall, variable, invoke) as CompletedCall<D>
        }
        return (this as SimpleResolutionCandidate).toCompletedCall(substitutor)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <D : CallableDescriptor> SimpleResolutionCandidate<D>.toCompletedCall(substitutor: TypeSubstitutor): CompletedCall.Simple<D> {
        val resultingDescriptor = descriptorWithFreshTypes.substitute(substitutor) as D
        val typeArguments = descriptorWithFreshTypes.typeParameters.map { substitutor.safeSubstitute(it.defaultType, Variance.INVARIANT).unwrap() }
        return CompletedCall.Simple(astCall, candidateDescriptor, resultingDescriptor, status, explicitReceiverKind,
                             dispatchReceiverArgument?.receiver, extensionReceiver?.receiver, typeArguments, argumentMappingByOriginal)
    }

    // true if we should complete this call
    private fun SimpleResolutionCandidate<*>.prepareForCompletion(expectedType: UnwrappedType?): Boolean {
        val returnType = descriptorWithFreshTypes.returnType?.unwrap() ?: return false
        if (expectedType != null && expectedType !== TypeUtils.NO_EXPECTED_TYPE) {
            csBuilder.addSubtypeConstraint(returnType, expectedType, ExpectedTypeConstraintPosition(astCall))
        }

        return expectedType != null || !returnType.contains { csBuilder.typeVariables.containsKey(it.constructor) }
    }

    private fun SimpleResolutionCandidate<*>.competeCall(lambdaAnalyzer: LambdaAnalyzer) {
        while (!oneStepToEndOrLambda(lambdaAnalyzer)) {
            // do nothing
        }
    }

    // true if it is end
    private fun SimpleResolutionCandidate<*>.oneStepToEndOrLambda(lambdaAnalyzer: LambdaAnalyzer): Boolean {
        val constraintStorage = csBuilder.startCompletion()
        if (constraintStorage.errors.isNotEmpty()) return true

        for (lambda in constraintStorage.lambdaArguments) {
            if (constraintStorage.canWeAnalyzeIt(lambda)) {
                constraintStorage.analyzeLambda(lambdaAnalyzer, astCall, lambda)
            }
        }

        val completionOrder = computeCompletionOrder(constraintStorage, descriptorWithFreshTypes.returnTypeOrNothing)
        for ((variable, direction) in completionOrder) {
            if (constraintStorage.errors.isNotEmpty()) return true
            if (variable is LambdaNewTypeVariable) {
                val resolvedLambda = constraintStorage.lambdaArguments.find { it.argument == variable.lambdaArgument } ?: return true
                if (constraintStorage.canWeAnalyzeIt(resolvedLambda)) {
                    constraintStorage.analyzeLambda(lambdaAnalyzer, astCall, resolvedLambda)
                    return false
                }
            }

            val variableWithConstraint = constraintStorage.notFixedTypeVariables[variable.freshTypeConstructor]
                                         ?: error("Incorrect type variable: $variable")
            val resultType = with(constraintFixator) {
                constraintStorage.findResultType(variableWithConstraint, direction)
            }
            if (resultType == null) {
                constraintStorage.errors.add(NotEnoughInformationForTypeParameter(variable))
                break
            }
            constraintStorage.fixVariable(variable, resultType)
        }
        return true
    }

    private fun ConstraintStorage.analyzeLambda(lambdaAnalyzer: LambdaAnalyzer, topLevelCall: ASTCall, lambda: ResolvedLambdaArgument) {
        val currentSubstitutor = buildCurrentSubstitutor()
        fun substitute(type: UnwrappedType) = currentSubstitutor.safeSubstitute(type, Variance.INVARIANT).unwrap()

        val receiver = lambda.receiver?.let(::substitute)
        val parameters = lambda.parameters.map(::substitute)
        val expectedType = lambda.returnType.check { canBeProper(it) }?.let(::substitute)
        val callsFromLambda = lambdaAnalyzer.analyzeAndGetRelatedCalls(topLevelCall, lambda.argument, receiver, parameters, expectedType)
        lambda.analyzed = true

        val position = ArgumentConstraintPosition(lambda.argument)
        for (innerCall in callsFromLambda) {
            when (innerCall) {
                is BaseResolvedCall.CompletedResolvedCall -> {
                    val returnType = innerCall.completedCall.lastCall.resultingDescriptor.returnTypeOrNothing
                    addSubtypeConstraint(returnType, lambda.returnType, position)
                }
                is BaseResolvedCall.OnlyResolvedCall -> {
                    // todo register call
                    val returnType = innerCall.candidate.lastCall.descriptorWithFreshTypes.returnTypeOrNothing
                    addInnerCall(innerCall)
                    addSubtypeConstraint(returnType, lambda.returnType, position)
                }
            }
        }
    }

    private fun ConstraintStorage.canWeAnalyzeIt(lambda: ResolvedLambdaArgument): Boolean {
        if (lambda.analyzed) return false
        lambda.receiver?.let {
            if (!canBeProper(it)) return false
        }
        return lambda.parameters.all { canBeProper(it) }
    }

    // type can contains type variables but for all of them we should know resultType
    private fun ConstraintStorage.canBeProper(type: UnwrappedType) = !type.contains {
        notFixedTypeVariables.containsKey(it.constructor)
    }
}