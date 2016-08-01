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

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.psi.Call
import org.jetbrains.kotlin.psi.ValueArgument
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.calls.BaseResolvedCall
import org.jetbrains.kotlin.resolve.calls.CompletedCall
import org.jetbrains.kotlin.resolve.calls.SimpleResolutionCandidate
import org.jetbrains.kotlin.resolve.calls.model.*
import org.jetbrains.kotlin.resolve.calls.results.ResolutionStatus
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.UnwrappedType
import org.jetbrains.kotlin.utils.addToStdlib.check
import java.util.*


class ASTToResolvedCallTransformer {

    fun <D : CallableDescriptor> transformAndReport(
            baseResolvedCall: BaseResolvedCall<D>,
            trace: BindingTrace? // if trace is not null then all information will be reported to this trace
    ): ResolvedCall<D> {
        if (baseResolvedCall is BaseResolvedCall.CompletedResolvedCall) {
            baseResolvedCall.allInnerCalls.forEach { transformAndReportCompletedCall(it, trace) }
            return transformAndReportCompletedCall(baseResolvedCall.completedCall, trace)
        }

        val onlyResolvedCall = (baseResolvedCall as BaseResolvedCall.OnlyResolvedCall)
        trace?.record(BindingContext.ONLY_RESOLVED_CALL, onlyResolvedCall.candidate.astCall.psiAstCall.psiCall, onlyResolvedCall)

        return StubOnlyResolvedCall(onlyResolvedCall.candidate.lastCall)
    }

    private fun <D: CallableDescriptor> transformAndReportCompletedCall(
            completedCall: CompletedCall<D>,
            trace: BindingTrace?
    ): ResolvedCall<D> {
        fun <C> C.runIfTraceNotNull(action: (BindingTrace, C)-> Unit): C {
            if (trace != null) action(trace, this)
            return this
        }

        return when (completedCall) {
            is CompletedCall.Simple -> {
                completedCall.runIfTraceNotNull(this::reportCallDiagnostic)
                NewResolvedCallImpl(completedCall).runIfTraceNotNull(this::bindResolvedCall)
            }
            is CompletedCall.VariableAsFunction -> {
                completedCall.variableCall.runIfTraceNotNull(this::reportCallDiagnostic)
                val resolvedCall = NewVariableAsFunctionResolvedCallImpl(
                        completedCall,
                        NewResolvedCallImpl(completedCall.variableCall),
                        NewResolvedCallImpl(completedCall.invokeCall)
                ).runIfTraceNotNull(this::bindResolvedCall)

                @Suppress("UNCHECKED_CAST")
                (resolvedCall as ResolvedCall<D>)
            }
        }
    }

    private fun bindResolvedCall(trace: BindingTrace, simpleResolvedCall: NewResolvedCallImpl<*>) {
        reportCallDiagnostic(trace, simpleResolvedCall.completedCall)
        val tracing = simpleResolvedCall.completedCall.astCall.psiAstCall.tracingStrategy

        tracing.bindReference(trace, simpleResolvedCall)
        tracing.bindResolvedCall(trace, simpleResolvedCall)
    }

    private fun bindResolvedCall(trace: BindingTrace, variableAsFunction: NewVariableAsFunctionResolvedCallImpl) {
        reportCallDiagnostic(trace, variableAsFunction.variableCall.completedCall)
        reportCallDiagnostic(trace, variableAsFunction.functionCall.completedCall)

        val outerTracingStrategy = variableAsFunction.completedCall.astCall.psiAstCall.tracingStrategy
        outerTracingStrategy.bindReference(trace, variableAsFunction.variableCall)
        outerTracingStrategy.bindResolvedCall(trace, variableAsFunction)
        variableAsFunction.functionCall.astCall.psiAstCall.tracingStrategy.bindReference(trace, variableAsFunction.functionCall)
    }

    private fun reportCallDiagnostic(trace: BindingTrace, completedCall: CompletedCall.Simple<*>) {
        // todo
    }
}

sealed class NewAbstractResolvedCall<D : CallableDescriptor>(): ResolvedCall<D> {
    abstract val argumentMappingByOriginal: Map<ValueParameterDescriptor, ResolvedCallArgument>
    abstract val astCall: ASTCall

    private var argumentToParameterMap: Map<ValueArgument, ArgumentMatchImpl>? = null
    private val _valueArguments: Map<ValueParameterDescriptor, ResolvedValueArgument> by lazy(this::createValueArguments)

    override fun getCall(): Call = astCall.psiAstCall.psiCall

    override fun getValueArguments(): Map<ValueParameterDescriptor, ResolvedValueArgument> = _valueArguments

    override fun getValueArgumentsByIndex(): List<ResolvedValueArgument>? {
        val arguments = ArrayList<ResolvedValueArgument?>(candidateDescriptor.valueParameters.size)
        for (i in 0..candidateDescriptor.valueParameters.size - 1) {
            arguments.add(null)
        }

        for ((parameterDescriptor, value) in valueArguments) {
            val oldValue = arguments.set(parameterDescriptor.index, value)
            if (oldValue != null) {
                return null
            }
        }

        if (arguments.any { it == null }) return null

        @Suppress("UNCHECKED_CAST")
        return arguments as List<ResolvedValueArgument>
    }

    override fun getArgumentMapping(valueArgument: ValueArgument): ArgumentMapping {
        if (argumentToParameterMap == null) {
            argumentToParameterMap = argumentToParameterMap(valueArguments)
        }
        val argumentMatch = argumentToParameterMap!![valueArgument] ?: return ArgumentUnmapped
        return argumentMatch
    }

    override fun getDataFlowInfoForArguments() = object : DataFlowInfoForArguments {
        override fun getResultInfo() = astCall.psiAstCall.resultDataFlowInfo
        override fun getInfo(valueArgument: ValueArgument): DataFlowInfo {
            val externalPsiCallArgument = astCall.externalArgument?.psiCallArgument
            if (externalPsiCallArgument?.valueArgument == valueArgument) {
                return externalPsiCallArgument.dataFlowInfoAfterThisArgument
            }
            astCall.argumentsInParenthesis.find { it.psiCallArgument.valueArgument == valueArgument }?.let {
                return it.psiCallArgument.dataFlowInfoAfterThisArgument
            }

            // valueArgument is not found
            // may be we should return initial DataFlowInfo but I think that it isn't important
            return astCall.psiAstCall.resultDataFlowInfo
        }
    }

    private fun argumentToParameterMap(valueArguments: Map<ValueParameterDescriptor, ResolvedValueArgument>): Map<ValueArgument, ArgumentMatchImpl> {
        val result = HashMap<ValueArgument, ArgumentMatchImpl>()
        for ((parameter, resolvedArgument) in valueArguments) {
            for (arguments in resolvedArgument.arguments) {
                result[arguments] = ArgumentMatchImpl(parameter)
            }
        }
        return result
    }

    private fun createValueArguments(): Map<ValueParameterDescriptor, ResolvedValueArgument> {
        val result = HashMap<ValueParameterDescriptor, ResolvedValueArgument>()
        for (parameter in candidateDescriptor.valueParameters) {
            val resolvedCallArgument = argumentMappingByOriginal[parameter.original] ?: continue
            val valueArgument = when (resolvedCallArgument) {
                ResolvedCallArgument.DefaultArgument -> DefaultValueArgument.DEFAULT
                is ResolvedCallArgument.SimpleArgument -> ExpressionValueArgument(resolvedCallArgument.callArgument.psiCallArgument.valueArgument)
                is ResolvedCallArgument.VarargArgument -> VarargValueArgument().apply {
                    resolvedCallArgument.arguments.map { it.psiCallArgument.valueArgument }.forEach(this::addArgument)
                }
            }
            result[parameter] = valueArgument
        }

        return result
    }
}

class NewResolvedCallImpl<D : CallableDescriptor>(
        val completedCall: CompletedCall.Simple<D>
): NewAbstractResolvedCall<D>() {
    override val astCall: ASTCall get() = completedCall.astCall

    override fun getStatus(): ResolutionStatus = completedCall.resolutionStatus.resultingApplicability.toResolutionStatus()

    override val argumentMappingByOriginal: Map<ValueParameterDescriptor, ResolvedCallArgument>
        get() = completedCall.argumentMappingByOriginal

    override fun getCandidateDescriptor(): D = completedCall.candidateDescriptor
    override fun getResultingDescriptor(): D = completedCall.resultingDescriptor
    override fun getExtensionReceiver(): ReceiverValue? = completedCall.extensionReceiver?.receiverValue
    override fun getDispatchReceiver(): ReceiverValue? = completedCall.dispatchReceiver?.receiverValue
    override fun getExplicitReceiverKind(): ExplicitReceiverKind = completedCall.explicitReceiverKind

    override fun getTypeArguments(): Map<TypeParameterDescriptor, KotlinType> {
        val typeParameters = candidateDescriptor.typeParameters.check { it.isNotEmpty() } ?: return emptyMap()

        val result = HashMap<TypeParameterDescriptor, UnwrappedType>()
        for ((parameter, argument) in typeParameters.zip(completedCall.typeArguments)) {
            result[parameter] = argument
        }
        return result
    }

    override fun getSmartCastDispatchReceiverType(): KotlinType? = null // todo
}

class NewVariableAsFunctionResolvedCallImpl(
        val completedCall: CompletedCall.VariableAsFunction,
        override val variableCall: NewResolvedCallImpl<VariableDescriptor>,
        override val functionCall: NewResolvedCallImpl<FunctionDescriptor>
): VariableAsFunctionResolvedCall, ResolvedCall<FunctionDescriptor> by functionCall

class StubOnlyResolvedCall<D : CallableDescriptor>(val candidate: SimpleResolutionCandidate<D>): NewAbstractResolvedCall<D>() {
    override fun getStatus() = ResolutionStatus.UNKNOWN_STATUS

    override fun getCandidateDescriptor(): D = candidate.candidateDescriptor
    override fun getResultingDescriptor(): D = candidateDescriptor
    override fun getExtensionReceiver() = candidate.extensionReceiver?.receiver?.receiverValue
    override fun getDispatchReceiver() = candidate.dispatchReceiverArgument?.receiver?.receiverValue
    override fun getExplicitReceiverKind() = candidate.explicitReceiverKind

    override fun getTypeArguments(): Map<TypeParameterDescriptor, KotlinType> = emptyMap()

    override fun getSmartCastDispatchReceiverType(): KotlinType? = null

    override val argumentMappingByOriginal: Map<ValueParameterDescriptor, ResolvedCallArgument>
        get() = candidate.argumentMappingByOriginal
    override val astCall: ASTCall get() = candidate.astCall
}