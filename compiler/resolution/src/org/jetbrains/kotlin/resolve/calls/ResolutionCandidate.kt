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

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.resolve.calls.inference.ConstraintFixator
import org.jetbrains.kotlin.resolve.calls.inference.ConstraintSystemBuilder
import org.jetbrains.kotlin.resolve.calls.inference.NewConstraintSystemBuilderImpl
import org.jetbrains.kotlin.resolve.calls.model.ASTCall
import org.jetbrains.kotlin.resolve.calls.model.CallDiagnostic
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCallArgument
import org.jetbrains.kotlin.resolve.calls.model.SimpleCallArgument
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind
import org.jetbrains.kotlin.resolve.calls.tower.Candidate
import org.jetbrains.kotlin.resolve.calls.tower.ResolutionCandidateStatus
import org.jetbrains.kotlin.resolve.calls.tower.isSuccess
import java.util.*


interface ResolutionPart<in D : CallableDescriptor> {
    fun SimpleResolutionCandidate<D>.process(): List<CallDiagnostic>
}

sealed class NewResolutionCandidate<out D : CallableDescriptor>(val implicitContextForCall: ImplicitContextForCall) : Candidate {
    abstract val astCall: ASTCall

    abstract val lastCall: SimpleResolutionCandidate<D>
}

sealed class AbstractSimpleResolutionCandidate<out D : CallableDescriptor>(
        implicitContextForCall: ImplicitContextForCall,
        private val resolutionSequence: List<ResolutionPart<D>>
) : NewResolutionCandidate<D>(implicitContextForCall) {
    override val isSuccessful: Boolean
        get() {
            process(stopOnFirstError = true)
            return hasErrors
        }

    private var _status: ResolutionCandidateStatus? = null

    override val status: ResolutionCandidateStatus
        get() {
            if (_status == null) {
                process(stopOnFirstError = false)
                _status = ResolutionCandidateStatus(diagnostics)
            }
            return _status!!
        }

    private val diagnostics = ArrayList<CallDiagnostic>()
    private var step = 0
    private var hasErrors = false

    private fun process(stopOnFirstError: Boolean) {
        while (step < resolutionSequence.size && (!stopOnFirstError || !hasErrors)) {
            val diagnostics = resolutionSequence[step].run { self().process() }
            step++
            hasErrors = diagnostics.any { !it.candidateApplicability.isSuccess }
            this.diagnostics.addAll(diagnostics)
        }
    }

    protected abstract fun self(): SimpleResolutionCandidate<D>
}

class SimpleResolutionCandidate<out D : CallableDescriptor>(
        implicitContextForCall: ImplicitContextForCall,
        val containingDescriptor: DeclarationDescriptor,
        override val astCall: ASTCall,
        val explicitReceiverKind: ExplicitReceiverKind,
        val dispatchReceiverArgument: SimpleCallArgument?,
        val extensionReceiver: SimpleCallArgument?,
        val candidateDescriptor: D,
        resolutionSequence: List<ResolutionPart<D>> //
) : AbstractSimpleResolutionCandidate<D>(implicitContextForCall, resolutionSequence) {
    val csBuilder: ConstraintSystemBuilder = NewConstraintSystemBuilderImpl(ConstraintFixator(implicitContextForCall.commonSupertypeCalculator))

    lateinit var typeArgumentMappingByOriginal: TypeArgumentsToParametersMapper.TypeArgumentsMapping
    lateinit var argumentMappingByOriginal: Map<ValueParameterDescriptor, ResolvedCallArgument>
    lateinit var descriptorWithFreshTypes: @UnsafeVariance D

    override fun self() = this
    override val lastCall: SimpleResolutionCandidate<D> get() = this
}

class VariableAsFunctionResolutionCandidate(
        override val astCall: ASTCall,
        implicitContextForCall: ImplicitContextForCall,
        val resolvedVariable: SimpleResolutionCandidate<VariableDescriptor>,
        val invokeCandidate: SimpleResolutionCandidate<FunctionDescriptor>
) : NewResolutionCandidate<FunctionDescriptor>(implicitContextForCall) {
    override val isSuccessful: Boolean get() = resolvedVariable.isSuccessful && invokeCandidate.isSuccessful
    override val status: ResolutionCandidateStatus
        get() = ResolutionCandidateStatus(resolvedVariable.status.diagnostics + invokeCandidate.status.diagnostics)

    override val lastCall: SimpleResolutionCandidate<FunctionDescriptor> get() = invokeCandidate
}
