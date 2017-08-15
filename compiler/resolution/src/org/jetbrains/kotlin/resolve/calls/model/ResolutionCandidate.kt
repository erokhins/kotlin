/*
 * Copyright 2010-2017 JetBrains s.r.o.
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
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.resolve.calls.components.TypeArgumentsToParametersMapper
import org.jetbrains.kotlin.resolve.calls.inference.NewConstraintSystem
import org.jetbrains.kotlin.resolve.calls.inference.components.FreshVariableNewTypeSubstitutor
import org.jetbrains.kotlin.resolve.calls.inference.model.ConstraintStorage
import org.jetbrains.kotlin.resolve.calls.inference.model.NewConstraintSystemImpl
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind
import org.jetbrains.kotlin.resolve.calls.tower.Candidate
import org.jetbrains.kotlin.resolve.calls.tower.ImplicitScopeTower
import org.jetbrains.kotlin.resolve.calls.tower.ResolutionCandidateApplicability
import org.jetbrains.kotlin.resolve.calls.tower.isSuccess
import org.jetbrains.kotlin.types.TypeSubstitutor


abstract class ResolutionPart {
    abstract fun KotlinResolutionCandidate.process()

    // helper functions
    protected inline val KotlinResolutionCandidate.candidateDescriptor get() = resolvedCall.candidateDescriptor
    protected inline val KotlinResolutionCandidate.kotlinCall get() = resolvedCall.ktPrimitive
}

/**
 * baseSystem contains all information from arguments, i.e. it is union of all system of arguments
 * Also by convention we suppose that baseSystem has no contradiction
 */
class KotlinResolutionCandidate(
        val callComponents: KotlinCallComponents,
        val resolvedCall: MutableResolvedKtCall,
        val knownTypeParametersResultingSubstitutor: TypeSubstitutor?,
        val scopeTower: ImplicitScopeTower,
        private val baseSystem: ConstraintStorage,
        initialDiagnostics: Collection<KotlinCallDiagnostic>
) : Candidate {
    private var newSystem: NewConstraintSystemImpl? = null
    private val diagnostics = arrayListOf<KotlinCallDiagnostic>()
    private var currentApplicability = ResolutionCandidateApplicability.RESOLVED

    private val resolutionSequence: List<ResolutionPart> get() = resolvedCall.ktPrimitive.callKind.resolutionSequence
    private var step = 0

    init {
        initialDiagnostics.forEach(this::addDiagnostic)
    }

    fun getSystem(): NewConstraintSystem {
        if (newSystem == null) {
            newSystem = NewConstraintSystemImpl(callComponents.constraintInjector, callComponents.resultTypeResolver)
            newSystem!!.addOtherSystem(baseSystem)
        }
        return newSystem!!
    }

    val csBuilder get() = getSystem().getBuilder()

    fun addDiagnostic(diagnostic: KotlinCallDiagnostic) {
        diagnostics.add(diagnostic)
        currentApplicability = maxOf(diagnostic.candidateApplicability, currentApplicability)
    }

    private fun process(stopOnFirstError: Boolean) {
        while (step < resolutionSequence.size) {
            if (stopOnFirstError && !currentApplicability.isSuccess) break

            resolutionSequence[step].run { this@KotlinResolutionCandidate.process() }
            step++
        }
    }

    override val isSuccessful: Boolean
        get() {
            process(stopOnFirstError = true)
            return currentApplicability.isSuccess
        }

    override val resultingApplicability: ResolutionCandidateApplicability
        get() {
            process(stopOnFirstError = false)
            if (csBuilder.hasContradiction) {
                return maxOf(ResolutionCandidateApplicability.INAPPLICABLE, currentApplicability)
            }
            return currentApplicability
        }

    override fun toString(): String {
        val descriptor = DescriptorRenderer.COMPACT.render(resolvedCall.candidateDescriptor)
        val okOrFail = if (currentApplicability.isSuccess) "OK" else "FAIL"
        val step = "$step/${resolutionSequence.size}"
        return "$okOrFail($step): $descriptor"
    }
}

class MutableResolvedKtCall(
        override val ktPrimitive: KotlinCall,
        override val candidateDescriptor: CallableDescriptor,
        override val explicitReceiverKind: ExplicitReceiverKind,
        override val dispatchReceiverArgument: SimpleKotlinCallArgument?,
        override val extensionReceiverArgument: SimpleKotlinCallArgument?
) : ResolvedKtCall() {
    override lateinit var typeArgumentMappingByOriginal: TypeArgumentsToParametersMapper.TypeArgumentsMapping
    override lateinit var argumentMappingByOriginal: Map<ValueParameterDescriptor, ResolvedCallArgument>
    override lateinit var substitutor: FreshVariableNewTypeSubstitutor
}

open class SimpleKotlinResolutionCandidate(
        val callComponents: KotlinCallComponents,
        val scopeTower: ImplicitScopeTower,
        override val kotlinCall: KotlinCall,
        val explicitReceiverKind: ExplicitReceiverKind,
        val dispatchReceiverArgument: SimpleKotlinCallArgument?,
        val extensionReceiver: SimpleKotlinCallArgument?,
        val candidateDescriptor: CallableDescriptor,
        val knownTypeParametersResultingSubstitutor: TypeSubstitutor?,
        initialDiagnostics: Collection<KotlinCallDiagnostic>
)
