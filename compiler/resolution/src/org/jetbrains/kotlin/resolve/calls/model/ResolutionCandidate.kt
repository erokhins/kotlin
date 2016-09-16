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

import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.resolve.calls.CallContext
import org.jetbrains.kotlin.resolve.calls.components.TypeArgumentsToParametersMapper
import org.jetbrains.kotlin.resolve.calls.inference.ConstraintSystemBuilder
import org.jetbrains.kotlin.resolve.calls.inference.NewConstraintSystem
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind
import org.jetbrains.kotlin.resolve.calls.tower.Candidate
import org.jetbrains.kotlin.resolve.calls.tower.ResolutionCandidateStatus
import org.jetbrains.kotlin.resolve.calls.tower.isSuccess
import java.util.*


interface ResolutionPart {
    fun SimpleResolutionCandidate.process(): List<CallDiagnostic>
}

sealed class NewResolutionCandidate() : Candidate {
    abstract val astCall: ASTCall

    abstract val lastCall: SimpleResolutionCandidate
}

sealed class AbstractSimpleResolutionCandidate(
        initialDiagnostics: Collection<CallDiagnostic> = emptyList()
) : NewResolutionCandidate() {
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
                _status = ResolutionCandidateStatus(diagnostics + constraintSystemDiagnostics)
            }
            return _status!!
        }

    private val diagnostics = ArrayList<CallDiagnostic>()
    private var step = 0
    private var hasErrors = false

    private fun process(stopOnFirstError: Boolean) {
        while (step < resolutionSequence.size && (!stopOnFirstError || !hasErrors)) {
            addDiagnostics(resolutionSequence[step].run { lastCall.process() })
            step++
        }
    }

    private fun addDiagnostics(diagnostics: Collection<CallDiagnostic>) {
        hasErrors = hasErrors || diagnostics.any { !it.candidateApplicability.isSuccess } ||
                    constraintSystemDiagnostics.any { !it.candidateApplicability.isSuccess }
        this.diagnostics.addAll(diagnostics)
    }

    init {
        addDiagnostics(initialDiagnostics)
    }

    abstract val resolutionSequence: List<ResolutionPart>
    abstract val constraintSystemDiagnostics: List<CallDiagnostic>
}

class SimpleResolutionCandidate(
        val callContext: CallContext,
        val explicitReceiverKind: ExplicitReceiverKind,
        val dispatchReceiverArgument: SimpleCallArgument?,
        val extensionReceiver: SimpleCallArgument?,
        val candidateDescriptor: CallableDescriptor,
        val constraintSystem: NewConstraintSystem,
        initialDiagnostics: Collection<CallDiagnostic>
) : AbstractSimpleResolutionCandidate(initialDiagnostics) {
    val csBuilder: ConstraintSystemBuilder get() = constraintSystem.getBuilder()
    override val constraintSystemDiagnostics: List<CallDiagnostic> get() = constraintSystem.diagnostics

    lateinit var typeArgumentMappingByOriginal: TypeArgumentsToParametersMapper.TypeArgumentsMapping
    lateinit var argumentMappingByOriginal: Map<ValueParameterDescriptor, ResolvedCallArgument>
    lateinit var descriptorWithFreshTypes: CallableDescriptor

    override val lastCall: SimpleResolutionCandidate get() = this
    override val astCall: ASTCall get() = callContext.astCall
    override val resolutionSequence: List<ResolutionPart> get() = astCall.callKind.resolutionSequence
}

val SimpleResolutionCandidate.containingDescriptor: DeclarationDescriptor get() = callContext.scopeTower.lexicalScope.ownerDescriptor

class VariableAsFunctionResolutionCandidate(
        override val astCall: ASTCall,
        val resolvedVariable: SimpleResolutionCandidate,
        val invokeCandidate: SimpleResolutionCandidate
) : NewResolutionCandidate() {
    override val isSuccessful: Boolean get() = resolvedVariable.isSuccessful && invokeCandidate.isSuccessful
    override val status: ResolutionCandidateStatus
        get() = ResolutionCandidateStatus(resolvedVariable.status.diagnostics + invokeCandidate.status.diagnostics)

    override val lastCall: SimpleResolutionCandidate get() = invokeCandidate
}
