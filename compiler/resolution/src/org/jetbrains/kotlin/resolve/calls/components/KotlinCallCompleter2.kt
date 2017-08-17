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

package org.jetbrains.kotlin.resolve.calls.components

import org.jetbrains.kotlin.resolve.calls.inference.NewConstraintSystem
import org.jetbrains.kotlin.resolve.calls.inference.components.KotlinConstraintSystemCompleter
import org.jetbrains.kotlin.resolve.calls.inference.components.KotlinConstraintSystemCompleter.ConstraintSystemCompletionMode
import org.jetbrains.kotlin.resolve.calls.inference.model.ConstraintStorage
import org.jetbrains.kotlin.resolve.calls.inference.model.ExpectedTypeConstraintPosition
import org.jetbrains.kotlin.resolve.calls.model.*
import org.jetbrains.kotlin.resolve.calls.tower.forceResolution
import org.jetbrains.kotlin.types.ErrorUtils
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.UnwrappedType

class KotlinCallCompleter2(
        private val postponedArgumentsAnalyzer: PostponedArgumentsAnalyzer,
        private val kotlinConstraintSystemCompleter: KotlinConstraintSystemCompleter
) {

    fun runCompletion(
            factory: SimpleCandidateFactory,
            candidates: Collection<KotlinResolutionCandidate>,
            expectedType: UnwrappedType?,
            resolutionCallbacks: KotlinResolutionCallbacks
    ): CallResolutionResult {
        val diagnosticHolder = KotlinDiagnosticsHolder.SimpleHolder()
        if (candidates.isEmpty()) {
            diagnosticHolder.addDiagnostic(NoneCandidates(factory.kotlinCall))
        }
        if (candidates.size > 1) {
            diagnosticHolder.addDiagnostic(ManyCandidates(factory.kotlinCall, candidates))
        }
        val candidate = candidates.singleOrNull()
        if (candidate == null || candidate.csBuilder.hasContradiction) {
            val candidateForCompletion = candidate ?: factory.createErrorCandidate().forceResolution()
            candidateForCompletion.prepareForCompletion(expectedType)
            runCompletion(candidateForCompletion.resolvedCall, ConstraintSystemCompletionMode.FULL, diagnosticHolder, candidateForCompletion.getSystem(), resolutionCallbacks)

            return CallResolutionResult(CallResolutionResult.Type.ERROR, candidate?.resolvedCall, diagnosticHolder.getDiagnostics(), ConstraintStorage.Empty)
        }

        val completionType = candidate.prepareForCompletion(expectedType)
        val constraintSystem = candidate.getSystem()
        runCompletion(candidate.resolvedCall, completionType, diagnosticHolder, constraintSystem, resolutionCallbacks)

        return if (completionType == ConstraintSystemCompletionMode.FULL) {
            CallResolutionResult(CallResolutionResult.Type.COMPLETED, candidate.resolvedCall, diagnosticHolder.getDiagnostics(), constraintSystem.asReadOnlyStorage())
        }
        else {
            CallResolutionResult(CallResolutionResult.Type.PARTIAL, candidate.resolvedCall, diagnosticHolder.getDiagnostics(), constraintSystem.asReadOnlyStorage())
        }

    }

    private  fun runCompletion(
            resolvedKtCall: ResolvedKtCall,
            completionMode: ConstraintSystemCompletionMode,
            diagnosticsHolder: KotlinDiagnosticsHolder,
            constraintSystem: NewConstraintSystem,
            resolutionCallbacks: KotlinResolutionCallbacks
    ) {
        val unsubstitutedReturnType = resolvedKtCall.candidateDescriptor.returnType?.unwrap()?.takeUnless { ErrorUtils.containsErrorType(it) }
                                      ?: constraintSystem.builtIns.unitType
        val returnType = resolvedKtCall.substitutor.safeSubstitute(unsubstitutedReturnType)
        kotlinConstraintSystemCompleter.runCompletion(constraintSystem.asConstraintSystemCompleterContext(), completionMode, resolvedKtCall, returnType) {
            postponedArgumentsAnalyzer.analyze(constraintSystem.asPostponedArgumentsAnalyzerContext(), resolutionCallbacks, it)
        }

        constraintSystem.diagnostics.forEach(diagnosticsHolder::addDiagnostic)
    }


    // true if we should complete this call
    private fun KotlinResolutionCandidate.prepareForCompletion(expectedType: UnwrappedType?): ConstraintSystemCompletionMode {
        val unsubstitutedReturnType = resolvedCall.candidateDescriptor.returnType?.unwrap() ?: return ConstraintSystemCompletionMode.PARTIAL
        val returnType = csBuilder.buildCurrentSubstitutor().safeSubstitute(unsubstitutedReturnType)
        if (expectedType != null && !TypeUtils.noExpectedType(expectedType)) {
            csBuilder.addSubtypeConstraint(returnType, expectedType, ExpectedTypeConstraintPosition(resolvedCall.ktPrimitive))
        }

        return if (expectedType != null || csBuilder.isProperType(returnType)) {
            ConstraintSystemCompletionMode.FULL
        }
        else {
            ConstraintSystemCompletionMode.PARTIAL
        }
    }
}