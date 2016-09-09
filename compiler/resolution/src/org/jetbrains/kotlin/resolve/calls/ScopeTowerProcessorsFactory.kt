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
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.resolve.calls.inference.CommonSupertypeCalculator
import org.jetbrains.kotlin.resolve.calls.model.*
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind
import org.jetbrains.kotlin.resolve.calls.tower.*
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValueWithSmartCastInfo


typealias NewFactoryProviderForInvoke = CandidateFactoryProviderForInvoke<NewResolutionCandidate<FunctionDescriptor>, SimpleResolutionCandidate<VariableDescriptor>>
typealias NewProcessor<D> = ScopeTowerProcessor<NewResolutionCandidate<D>>

// CallContext
class ImplicitContextForCall(
        val argumentsToParametersMapper: ArgumentsToParametersMapper,
        val typeArgumentsToParametersMapper: TypeArgumentsToParametersMapper,
        val commonSupertypeCalculator: CommonSupertypeCalculator,
        val callableReferenceResolver: CallableReferenceResolver,
        val scopeTower: ImplicitScopeTower,
        val factoryProviderForInvoke: NewFactoryProviderForInvoke
)

sealed class ASTCallKind<D : CallableDescriptor>(vararg resolutionPart: ResolutionPart<D>) {
    val resolutionSequence = resolutionPart.asList()

    // move outside and add parameter factoryProviderForInvoke
    abstract fun createProcessor(contextForCall: ImplicitContextForCall, call: ASTCall): NewProcessor<D>

    object VARIABLE : ASTCallKind<VariableDescriptor>(
            CheckVisibility,
            NoTypeArguments,
            NoArguments,
            CreteDescriptorWithFreshTypeVariables,
            CheckExplicitReceiverKindConsistency,
            CheckReceivers
    ) {
        override fun createProcessor(contextForCall: ImplicitContextForCall, call: ASTCall): NewProcessor<VariableDescriptor> =
                createVariableAndObjectProcessor(contextForCall.scopeTower, call.name, NewCandidateFactory(contextForCall, call, resolutionSequence),
                                                 call.explicitReceiver?.receiver)
    }

    object FUNCTION : ASTCallKind<FunctionDescriptor>(
            CheckVisibility,
            MapTypeArguments,
            MapArguments,
            CreteDescriptorWithFreshTypeVariables,
            CheckExplicitReceiverKindConsistency,
            CheckReceivers,
            CheckArguments
    ) {
        override fun createProcessor(contextForCall: ImplicitContextForCall, call: ASTCall): NewProcessor<FunctionDescriptor> =
                createFunctionProcessor(contextForCall.scopeTower, call.name, NewCandidateFactory(contextForCall, call, resolutionSequence),
                                        contextForCall.factoryProviderForInvoke, call.explicitReceiver?.receiver)
    }

    class Unsupported<D : CallableDescriptor>() : ASTCallKind<D>() {
        override fun createProcessor(contextForCall: ImplicitContextForCall, call: ASTCall): NewProcessor<D> =
                TODO("not implemented")
    }
}

class ReportTowerDiagnostics(val towerDiagnostics: List<ResolutionDiagnostic>): ResolutionPart<CallableDescriptor> {
    override fun SimpleResolutionCandidate<CallableDescriptor>.process() = towerDiagnostics
}



class NewCandidateFactory<D : CallableDescriptor>(
        val contextForCall: ImplicitContextForCall,
        val astCall: ASTCall, // move to context
        private val resolutionSequence: List<ResolutionPart<D>> // move to context
) : CandidateFactory<D, SimpleResolutionCandidate<D>> {

    // todo: try something else, because current method is ugly and unstable
    private fun createReceiverArgument(
            explicitReceiver: ReceiverCallArgument?,
            fromResolution: ReceiverValueWithSmartCastInfo?
    ): SimpleCallArgument? =
            explicitReceiver as? SimpleCallArgument ?: // qualifier receiver cannot be safe
            fromResolution?.let { ReceiverExpressionArgument(it, isSafeCall = false) } // only explicit receiver can be smart cast

    override fun createCandidate(
            towerCandidate: CandidateWithBoundDispatchReceiver<D>,
            explicitReceiverKind: ExplicitReceiverKind,
            extensionReceiver: ReceiverValueWithSmartCastInfo?
    ): SimpleResolutionCandidate<D> {
        val dispatchArgumentReceiver = createReceiverArgument(astCall.getExplicitDispatchReceiver(explicitReceiverKind),
                                                              towerCandidate.dispatchReceiver)
        val extensionArgumentReceiver = createReceiverArgument(astCall.getExplicitExtensionReceiver(explicitReceiverKind), extensionReceiver)

        val resolutionSequence = if (towerCandidate.diagnostics.isEmpty()) {
            resolutionSequence
        }
        else {
            listOf(ReportTowerDiagnostics(towerCandidate.diagnostics)) + resolutionSequence
        }
        return SimpleResolutionCandidate(contextForCall, contextForCall.scopeTower.lexicalScope.ownerDescriptor, astCall,
                                         explicitReceiverKind, dispatchArgumentReceiver, extensionArgumentReceiver,
                                         towerCandidate.descriptor, resolutionSequence)
    }
}




