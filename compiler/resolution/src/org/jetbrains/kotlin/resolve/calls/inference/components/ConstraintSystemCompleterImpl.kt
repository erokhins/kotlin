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

package org.jetbrains.kotlin.resolve.calls.inference.components

import org.jetbrains.kotlin.resolve.calls.inference.model.NewTypeVariable
import org.jetbrains.kotlin.resolve.calls.inference.model.NotEnoughInformationForTypeParameter
import org.jetbrains.kotlin.resolve.calls.inference.model.VariableWithConstraints
import org.jetbrains.kotlin.resolve.calls.model.*
import org.jetbrains.kotlin.types.TypeConstructor
import org.jetbrains.kotlin.types.UnwrappedType
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

class KotlinConstraintSystemCompleter(
        private val resultTypeResolver: ResultTypeResolver,
        private val variableFixationFinder: VariableFixationFinder
) {
    enum class ConstraintSystemCompletionMode {
        FULL,
        PARTIAL
    }

    interface Context : VariableFixationFinder.Context, ResultTypeResolver.Context {
        override val notFixedTypeVariables: Map<TypeConstructor, VariableWithConstraints>

        // type can be proper if it not contains not fixed type variables
        fun canBeProper(type: UnwrappedType): Boolean

        // mutable operations
        fun addError(error: KotlinCallDiagnostic)
        fun fixVariable(variable: NewTypeVariable, resultType: UnwrappedType)
    }

    fun runCompletion(
            c: Context,
            completionMode: ConstraintSystemCompletionMode,
            topLevelPrimitive: ResolvedKtPrimitive,
            topLevelType: UnwrappedType,
            analyze: (PostponedResolveKtPrimitive) -> Unit
    ) {
        while (true) {
            if (analyzePostponeArgumentIfPossible(c, topLevelPrimitive, analyze)) continue

            val allTypeVariables = getOrderedAllTypeVariables(c, topLevelPrimitive)
            val postponedKtPrimitives = getOrderedNotAnalyzedPostponedArguments(topLevelPrimitive)
            val variableForFixation = variableFixationFinder.findFirstVariableForFixation(
                    c, allTypeVariables, postponedKtPrimitives, completionMode, topLevelType)

            if (shouldWeForceCallableReferenceResolution(completionMode, variableForFixation)) {
                if (forceCallableReferenceResolution(topLevelPrimitive, analyze)) continue
            }

            if (variableForFixation != null) {
                if (variableForFixation.hasProperConstraint || completionMode == ConstraintSystemCompletionMode.FULL) {
                    val variableWithConstraints = c.notFixedTypeVariables[variableForFixation.variable]!!

                    fixVariable(c, topLevelType, variableWithConstraints, postponedKtPrimitives)

                    if (!variableForFixation.hasProperConstraint) {
                        c.addError(NotEnoughInformationForTypeParameter(variableWithConstraints.typeVariable))
                    }
                    continue
                }
            }
            break
        }

        if (completionMode == ConstraintSystemCompletionMode.FULL) {
            // force resolution for all not-analyzed argument's
            getOrderedNotAnalyzedPostponedArguments(topLevelPrimitive).forEach(analyze)
        }
    }

    private fun shouldWeForceCallableReferenceResolution(
            completionMode: ConstraintSystemCompletionMode,
            variableForFixation: VariableFixationFinder.VariableForFixation?
    ): Boolean {
        if (completionMode == ConstraintSystemCompletionMode.PARTIAL) return false
        if (variableForFixation != null && variableForFixation.hasProperConstraint) return false

        return true
    }

    // true if we do analyze
    private fun analyzePostponeArgumentIfPossible(c: Context, topLevelPrimitive: ResolvedKtPrimitive, analyze: (PostponedResolveKtPrimitive) -> Unit): Boolean {
        for (argument in getOrderedNotAnalyzedPostponedArguments(topLevelPrimitive)) {
            if (canWeAnalyzeIt(c, argument)) {
                analyze(argument)
                return true
            }
        }
        return false
    }

    // true if we find some callable reference and run resolution for it. Note that such resolution can be unsuccessful
    private fun forceCallableReferenceResolution(topLevelPrimitive: ResolvedKtPrimitive, analyze: (PostponedResolveKtPrimitive) -> Unit): Boolean {
        val callableReferenceArgument = getOrderedNotAnalyzedPostponedArguments(topLevelPrimitive).
                firstIsInstanceOrNull<ResolvedKtCallableReference>() ?: return false

        analyze(callableReferenceArgument)
        return true
    }

    private fun getOrderedNotAnalyzedPostponedArguments(topLevelPrimitive: ResolvedKtPrimitive): List<PostponedResolveKtPrimitive> {
        fun ResolvedKtPrimitive.process(to: MutableList<PostponedResolveKtPrimitive>) {
            to.addIfNotNull(this as? PostponedResolveKtPrimitive)

            if (state == ResolvedKtPrimitiveState.ADDITIONAL_ANALYSIS_PERFORMED) {
                subKtPrimitives.forEach { it.process(to) }
            }
        }
        return arrayListOf<PostponedResolveKtPrimitive>().apply { topLevelPrimitive.process(this) }
    }

    private fun getOrderedAllTypeVariables(c: Context, topLevelPrimitive: ResolvedKtPrimitive) : List<TypeConstructor> {
        fun ResolvedKtPrimitive.process(to: MutableList<TypeConstructor>) {
            val typeVariables = when (this) {
                is ResolvedKtCall -> substitutor.freshVariables
                is ResolvedKtCallableReference -> candidate?.freshSubstitutor?.freshVariables.orEmpty()
                is ResolvedKtLambda -> listOfNotNull(typeVariableForLambdaReturnType)
                else -> emptyList()
            }
            typeVariables.mapNotNullTo(to) {
                val typeConstructor = it.freshTypeConstructor
                typeConstructor.takeIf { c.notFixedTypeVariables.containsKey(typeConstructor) }
            }

            if (state == ResolvedKtPrimitiveState.ADDITIONAL_ANALYSIS_PERFORMED) {
                subKtPrimitives.forEach { it.process(to) }
            }
        }
        val result = arrayListOf<TypeConstructor>().apply { topLevelPrimitive.process(this) }

        assert(result.size == c.notFixedTypeVariables.size) {
            val notFoundTypeVariables = c.notFixedTypeVariables.keys.toMutableSet().removeAll(result)
            "Not all type variables found: $notFoundTypeVariables"
        }

        return result
    }


    private fun canWeAnalyzeIt(c: Context, argument: PostponedResolveKtPrimitive): Boolean {
        if (argument.analyzed) return false

        return argument.inputTypes.all { c.canBeProper(it) }
    }

    private fun fixVariable(
            c: Context,
            topLevelType: UnwrappedType,
            variableWithConstraints: VariableWithConstraints,
            postponedResolveKtPrimitives: List<PostponedResolveKtPrimitive>
    ) {
        val direction = TypeVariableDirectionCalculator(c, postponedResolveKtPrimitives, topLevelType).getDirection(variableWithConstraints)

        val resultType = resultTypeResolver.findResultType(c, variableWithConstraints, direction)

        c.fixVariable(variableWithConstraints.typeVariable, resultType)
    }
}