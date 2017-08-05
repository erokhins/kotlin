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

import org.jetbrains.kotlin.resolve.calls.inference.ConstraintSystemBuilder
import org.jetbrains.kotlin.resolve.calls.inference.components.NewTypeSubstitutor
import org.jetbrains.kotlin.resolve.calls.inference.model.LambdaArgumentConstraintPosition
import org.jetbrains.kotlin.resolve.calls.model.*
import org.jetbrains.kotlin.types.UnwrappedType
import org.jetbrains.kotlin.types.typeUtil.builtIns
import org.jetbrains.kotlin.utils.SmartList
import org.jetbrains.kotlin.utils.addIfNotNull

class PostponedArgumentsAnalyzer(
        private val callableReferenceResolver: CallableReferenceResolver
) {
    interface Context {
        fun buildCurrentSubstitutor(): NewTypeSubstitutor

        // type can be proper if it not contains not fixed type variables
        fun canBeProper(type: UnwrappedType): Boolean

        // mutable operations
        fun getBuilder(): ConstraintSystemBuilder
        fun addError(error: KotlinCallDiagnostic)
    }

    fun analyze(c: Context, resolutionCallbacks: KotlinResolutionCallbacks, argument: PostponedKotlinCallArgument) {
        val diagnostics = when (argument) {
            is PostponedLambdaArgument -> analyzeLambda(c, resolutionCallbacks, argument)
            is PostponedCallableReferenceArgument -> callableReferenceResolver.processCallableReferenceArgument(c.getBuilder(), argument)
            is PostponedCollectionLiteralArgument -> TODO("Not supported")
        }
        diagnostics.forEach { c.addError(it) }
    }

    private fun analyzeLambda(c: Context, resolutionCallbacks: KotlinResolutionCallbacks, lambda: PostponedLambdaArgument): Collection<KotlinCallDiagnostic> {
        val diagnostics = SmartList<KotlinCallDiagnostic>()
        val currentSubstitutor = c.buildCurrentSubstitutor()
        fun substitute(type: UnwrappedType) = currentSubstitutor.safeSubstitute(type)

        val receiver = lambda.receiver?.let(::substitute)
        val parameters = lambda.parameters.map(::substitute)
        val expectedType = lambda.returnType.takeIf { c.canBeProper(it) }?.let(::substitute)
        lambda.analyzed = true
        lambda.resultArguments = resolutionCallbacks.analyzeAndGetLambdaResultArguments(lambda.argument, lambda.isSuspend, receiver, parameters, expectedType)

        for (resultLambdaArgument in lambda.resultArguments) {
            diagnostics.addIfNotNull(checkSimpleArgument(c.getBuilder(), resultLambdaArgument, lambda.returnType.let(::substitute)))
        }

        if (lambda.resultArguments.isEmpty()) {
            val unitType = lambda.returnType.builtIns.unitType
            c.getBuilder().addSubtypeConstraint(lambda.returnType.let(::substitute), unitType, LambdaArgumentConstraintPosition(lambda))
        }
        return diagnostics
    }
}