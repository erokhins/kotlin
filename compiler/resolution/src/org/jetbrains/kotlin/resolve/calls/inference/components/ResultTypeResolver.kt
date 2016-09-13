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

package org.jetbrains.kotlin.resolve.calls.inference.components

import org.jetbrains.kotlin.resolve.calls.CommonSupertypeCalculator
import org.jetbrains.kotlin.resolve.calls.inference.ResolveDirection
import org.jetbrains.kotlin.resolve.calls.inference.model.ConstraintKind
import org.jetbrains.kotlin.resolve.calls.inference.model.VariableWithConstraints
import org.jetbrains.kotlin.types.UnwrappedType
import org.jetbrains.kotlin.types.checker.intersectTypes
import org.jetbrains.kotlin.types.singleBestRepresentative

class ResultTypeResolver(val commonSupertypeCalculator: CommonSupertypeCalculator) {
    interface Context {
        fun isProperType(type: UnwrappedType): Boolean
    }

    fun findResultType(c: Context, variableWithConstraints: VariableWithConstraints, direction: ResolveDirection): UnwrappedType? {
        findResultIfThereIsEqualsConstraint(c, variableWithConstraints, allowedFixToNotProperType = false)?.let { return it }

        if (direction == ResolveDirection.TO_SUBTYPE) {
            val lowerConstraints = variableWithConstraints.constraints.filter { it.kind == ConstraintKind.LOWER && c.isProperType(it.type) }
            if (lowerConstraints.isNotEmpty()) {
                return commonSupertypeCalculator(lowerConstraints.map { it.type })
            }
        }

        // direction != TO_SUBTYPE or there is no LOWER bounds
        val upperConstraints = variableWithConstraints.constraints.filter { it.kind == ConstraintKind.UPPER && c.isProperType(it.type) }
        if (upperConstraints.isNotEmpty()) {
            return intersectTypes(upperConstraints.map { it.type })
        }

        return null
    }

    fun findResultIfThereIsEqualsConstraint(
            c: Context,
            variableWithConstraints: VariableWithConstraints,
            allowedFixToNotProperType: Boolean = false
    ): UnwrappedType? {
        val properEqualsConstraint = variableWithConstraints.constraints.filter {
            it.kind == ConstraintKind.EQUALITY && c.isProperType(it.type)
        }

        if (properEqualsConstraint.isNotEmpty()) {
            return properEqualsConstraint.map { it.type }.singleBestRepresentative()?.unwrap()
                   ?: properEqualsConstraint.first().type // seems like constraint system has contradiction
        }
        if (!allowedFixToNotProperType) return null

        val notProperEqualsConstraint = variableWithConstraints.constraints.filter { it.kind == ConstraintKind.EQUALITY }

        // may be we should just firstOrNull
        return notProperEqualsConstraint.singleOrNull()?.type
    }
}