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
import org.jetbrains.kotlin.descriptors.ReceiverParameterDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.resolve.calls.inference.components.ConstraintInjector
import org.jetbrains.kotlin.resolve.calls.inference.components.FixationOrderCalculator
import org.jetbrains.kotlin.resolve.calls.inference.components.ResultTypeResolver
import org.jetbrains.kotlin.resolve.calls.inference.model.*
import org.jetbrains.kotlin.resolve.calls.inference.returnTypeOrNothing
import org.jetbrains.kotlin.resolve.calls.model.*
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind
import org.jetbrains.kotlin.resolve.calls.tower.ResolutionCandidateApplicability
import org.jetbrains.kotlin.resolve.calls.tower.ResolutionCandidateStatus
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValueWithSmartCastInfo
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.checker.KotlinTypeChecker
import org.jetbrains.kotlin.utils.SmartList
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlin.utils.addToStdlib.check

interface LambdaAnalyzer {
    fun analyzeAndGetRelatedCalls(
            topLevelCall: ASTCall,
            lambdaArgument: LambdaArgument,
            receiverType: UnwrappedType?,
            parameters: List<UnwrappedType>,
            expectedReturnType: UnwrappedType? // null means, that return type is not proper i.e. it depends on some type variables
    ): List<BaseResolvedCall>
}

sealed class CompletedCall {
    abstract val lastCall: Simple

    class Simple(
            val astCall: ASTCall,
            val candidateDescriptor: CallableDescriptor,
            val resultingDescriptor: CallableDescriptor,
            val resolutionStatus: ResolutionCandidateStatus,
            val explicitReceiverKind: ExplicitReceiverKind,
            val dispatchReceiver: ReceiverValueWithSmartCastInfo?,
            val extensionReceiver: ReceiverValueWithSmartCastInfo?,
            val typeArguments: List<UnwrappedType>,
            val argumentMappingByOriginal: Map<ValueParameterDescriptor, ResolvedCallArgument>
    ): CompletedCall() {
        override val lastCall: Simple get() = this
    }

    class VariableAsFunction(
            val astCall: ASTCall,
            val variableCall: Simple,
            val invokeCall: Simple
    ): CompletedCall() {
        override val lastCall: Simple get() = invokeCall
    }
}

sealed class BaseResolvedCall {

    class CompletedResolvedCall(
            val completedCall: CompletedCall,
            val allInnerCalls: Collection<CompletedCall>
    ): BaseResolvedCall()

    class OnlyResolvedCall(
            val candidate: NewResolutionCandidate
    ) : BaseResolvedCall() {
        val currentReturnType: UnwrappedType = candidate.lastCall.descriptorWithFreshTypes.returnTypeOrNothing
    }
}

class ASTCallCompleter(
        val resultTypeResolver: ResultTypeResolver,
        val constraintInjector: ConstraintInjector,
        val fixationOrderCalculator: FixationOrderCalculator
) {
    interface Context {
        val innerCalls: List<BaseResolvedCall.OnlyResolvedCall>
        val hasContradiction: Boolean
        fun buildCurrentSubstitutor(): TypeSubstitutor
        val lambdaArguments: List<ResolvedLambdaArgument>

        // type can be proper if it not contains not fixed type variables
        fun canBeProper(type: UnwrappedType): Boolean
        fun asFixationOrderCalculatorContext(): FixationOrderCalculator.Context
        fun asResultTypeResolverContext(): ResultTypeResolver.Context

        // mutable operations
        fun asConstraintInjectorContext(): ConstraintInjector.Context
        fun addError(error: CallDiagnostic)
        fun addInnerCall(innerCall: BaseResolvedCall.OnlyResolvedCall)
        fun fixVariable(variable: NewTypeVariable, resultType: UnwrappedType)
    }

    fun transformWhenAmbiguity(candidate: NewResolutionCandidate): BaseResolvedCall =
            toCompletedBaseResolvedCall(candidate.lastCall.constraintSystem.asCallCompleterContext(), candidate)

    fun completeCallIfNecessary(
            candidate: NewResolutionCandidate,
            expectedType: UnwrappedType?,
            lambdaAnalyzer: LambdaAnalyzer
    ): BaseResolvedCall {
        val topLevelCall =
                if (candidate is VariableAsFunctionResolutionCandidate) {
                    candidate.invokeCandidate
                }
                else {
                    candidate as SimpleResolutionCandidate
                }

        if (topLevelCall.prepareForCompletion(expectedType)) {
            val c = candidate.lastCall.constraintSystem.asCallCompleterContext()

            topLevelCall.competeCall(c, lambdaAnalyzer)
            return toCompletedBaseResolvedCall(c, candidate)
        }

        return BaseResolvedCall.OnlyResolvedCall(candidate)
    }

    private fun toCompletedBaseResolvedCall(
            c: Context,
            candidate: NewResolutionCandidate
    ): BaseResolvedCall.CompletedResolvedCall {
        val currentSubstitutor = c.buildCurrentSubstitutor()
        val completedCall = candidate.toCompletedCall(currentSubstitutor)
        val competedCalls = c.innerCalls.map {
            it.candidate.toCompletedCall(currentSubstitutor)
        }
        return BaseResolvedCall.CompletedResolvedCall(completedCall, competedCalls)
    }

    private fun NewResolutionCandidate.toCompletedCall(substitutor: TypeSubstitutor): CompletedCall {
        if (this is VariableAsFunctionResolutionCandidate) {
            val variable = resolvedVariable.toCompletedCall(substitutor)
            val invoke = invokeCandidate.toCompletedCall(substitutor)

            return CompletedCall.VariableAsFunction(astCall, variable, invoke)
        }
        return (this as SimpleResolutionCandidate).toCompletedCall(substitutor)
    }

    private fun SimpleResolutionCandidate.toCompletedCall(substitutor: TypeSubstitutor): CompletedCall.Simple {
        val resultingDescriptor = descriptorWithFreshTypes.substitute(substitutor)!!
        val typeArguments = descriptorWithFreshTypes.typeParameters.map { substitutor.safeSubstitute(it.defaultType, Variance.INVARIANT).unwrap() }

        val status = computeStatus(this, resultingDescriptor)
        return CompletedCall.Simple(astCall, candidateDescriptor, resultingDescriptor, status, explicitReceiverKind,
                             dispatchReceiverArgument?.receiver, extensionReceiver?.receiver, typeArguments, argumentMappingByOriginal)
    }

    private fun computeStatus(candidate: SimpleResolutionCandidate, resultingDescriptor: CallableDescriptor): ResolutionCandidateStatus {
        val smartCasts = reportSmartCasts(candidate, resultingDescriptor).check { it.isNotEmpty() } ?: return candidate.status
        return ResolutionCandidateStatus(candidate.status.diagnostics + smartCasts)
    }

    private fun createSmartCastDiagnostic(argument: CallArgument, expectedResultType: UnwrappedType): SmartCastDiagnostic? {
        if (argument !is ExpressionArgument) return null
        if (!KotlinTypeChecker.DEFAULT.isSubtypeOf(argument.receiver.receiverValue.type, expectedResultType)) {
            return SmartCastDiagnostic(argument, expectedResultType.unwrap())
        }
        return null
    }

    private fun reportSmartCastOnReceiver(
            candidate: NewResolutionCandidate,
            receiver: SimpleCallArgument?,
            parameter: ReceiverParameterDescriptor?
    ): SmartCastDiagnostic? {
        if (receiver == null || parameter == null) return null
        val expectedType = parameter.type.unwrap().let { if (receiver.isSafeCall) it.makeNullableAsSpecified(true) else it }

        val smartCastDiagnostic = createSmartCastDiagnostic(receiver, expectedType) ?: return null

        // todo may be we have smart cast to Int?
        return smartCastDiagnostic.check {
            candidate.status.diagnostics.filterIsInstance<UnsafeCallError>().none {
                it.receiver == receiver
            }
        }
    }


    private fun reportSmartCasts(candidate: SimpleResolutionCandidate, resultingDescriptor: CallableDescriptor): List<CallDiagnostic> = SmartList<CallDiagnostic>().apply {
        addIfNotNull(reportSmartCastOnReceiver(candidate, candidate.extensionReceiver, resultingDescriptor.extensionReceiverParameter))
        addIfNotNull(reportSmartCastOnReceiver(candidate, candidate.dispatchReceiverArgument, resultingDescriptor.dispatchReceiverParameter))

        for (parameter in resultingDescriptor.valueParameters) {
            for (argument in candidate.argumentMappingByOriginal[parameter.original]?.arguments ?: continue) {
                val smartCastDiagnostic = createSmartCastDiagnostic(argument, argument.getExpectedType(parameter)) ?: continue

                val thereIsUnstableSmartCastError = candidate.status.diagnostics.filterIsInstance<UnstableSmartCast>().any {
                    it.expressionArgument == argument
                }

                if (!thereIsUnstableSmartCastError) {
                    add(smartCastDiagnostic)
                }
            }
        }
    }

    // true if we should complete this call
    private fun SimpleResolutionCandidate.prepareForCompletion(expectedType: UnwrappedType?): Boolean {
        val returnType = descriptorWithFreshTypes.returnType?.unwrap() ?: return false
        if (expectedType != null && expectedType !== TypeUtils.NO_EXPECTED_TYPE) {
            csBuilder.addSubtypeConstraint(returnType, expectedType, ExpectedTypeConstraintPosition(astCall))
        }

        return expectedType != null || csBuilder.isProperType(returnType)
    }

    private fun SimpleResolutionCandidate.competeCall(c: Context, lambdaAnalyzer: LambdaAnalyzer) {
        while (!oneStepToEndOrLambda(c, lambdaAnalyzer)) {
            // do nothing -- be happy
        }
    }

    // true if it is the end (happy or not)
    private fun SimpleResolutionCandidate.oneStepToEndOrLambda(c: Context, lambdaAnalyzer: LambdaAnalyzer): Boolean {
        if (c.hasContradiction) return true

        for (lambda in c.lambdaArguments) {
            if (canWeAnalyzeIt(c, lambda)) {
                analyzeLambda(c, lambdaAnalyzer, astCall, lambda)
            }
        }

        val completionOrder = fixationOrderCalculator.computeCompletionOrder(c.asFixationOrderCalculatorContext(), descriptorWithFreshTypes.returnTypeOrNothing)
        for ((variableWithConstraints, direction) in completionOrder) {
            if (c.hasContradiction) return true

            val variable = variableWithConstraints.typeVariable
            if (variable is LambdaTypeVariable) {
                val resolvedLambda = c.lambdaArguments.find { it.argument == variable.lambdaArgument } ?: return true
                if (canWeAnalyzeIt(c, resolvedLambda)) {
                    analyzeLambda(c, lambdaAnalyzer, astCall, resolvedLambda)
                    return false
                }
            }

            val resultType = resultTypeResolver.findResultType(c.asResultTypeResolverContext(), variableWithConstraints, direction)
            if (resultType == null) {
                c.addError(NotEnoughInformationForTypeParameter(variable))
                break
            }
            c.fixVariable(variable, resultType)
        }
        return true
    }

    private fun analyzeLambda(c: Context, lambdaAnalyzer: LambdaAnalyzer, topLevelCall: ASTCall, lambda: ResolvedLambdaArgument) {
        val currentSubstitutor = c.buildCurrentSubstitutor()
        fun substitute(type: UnwrappedType) = currentSubstitutor.safeSubstitute(type, Variance.INVARIANT).unwrap()

        val receiver = lambda.receiver?.let(::substitute)
        val parameters = lambda.parameters.map(::substitute)
        val expectedType = lambda.returnType.check { c.canBeProper(it) }?.let(::substitute)
        val callsFromLambda = lambdaAnalyzer.analyzeAndGetRelatedCalls(topLevelCall, lambda.argument, receiver, parameters, expectedType)
        lambda.analyzed = true

        val injectorContext = c.asConstraintInjectorContext()
        val position = ArgumentConstraintPosition(lambda.argument)
        for (innerCall in callsFromLambda) {
            when (innerCall) {
                is BaseResolvedCall.CompletedResolvedCall -> {
                    val returnType = innerCall.completedCall.lastCall.resultingDescriptor.returnTypeOrNothing
                    constraintInjector.addInitialSubtypeConstraint(injectorContext, returnType, lambda.returnType, position)
                }
                is BaseResolvedCall.OnlyResolvedCall -> {
                    // todo register call
                    val returnType = innerCall.candidate.lastCall.descriptorWithFreshTypes.returnTypeOrNothing
                    c.addInnerCall(innerCall)
                    constraintInjector.addInitialSubtypeConstraint(injectorContext, returnType, lambda.returnType, position)
                }
            }
        }
    }

    private fun canWeAnalyzeIt(c: Context, lambda: ResolvedLambdaArgument): Boolean {
        if (lambda.analyzed) return false
        lambda.receiver?.let {
            if (!c.canBeProper(it)) return false
        }
        return lambda.parameters.all { c.canBeProper(it) }
    }
}

class SmartCastDiagnostic(val expressionArgument: ExpressionArgument, val smartCastType: UnwrappedType): CallDiagnostic(ResolutionCandidateApplicability.RESOLVED) {
    override fun report(reporter: DiagnosticReporter) = reporter.onCallArgument(expressionArgument, this)
}