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

package org.jetbrains.kotlin.resolve.calls.inference

import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.checker.CaptureStatus
import org.jetbrains.kotlin.types.checker.NewCapturedType
import org.jetbrains.kotlin.types.checker.NewCapturedTypeConstructor
import org.jetbrains.kotlin.types.typeUtil.asTypeProjection
import org.jetbrains.kotlin.types.typeUtil.contains
import org.jetbrains.kotlin.utils.SmartSet
import org.jetbrains.kotlin.utils.addIfNotNull
import java.util.*

// todo problem: intersection types in constrains: A <: Number, B <: Inv<A & Any> =>? B <: Inv<out Number & Any>
object ConstraintIncorporator {

    interface IncorporationContext {
        val allTypeVariablesWithConstraints: Collection<VariableWithConstraints>

        fun getTypeVariable(typeConstructor: TypeConstructor): NewTypeVariable?

        fun getConstraintsForVariable(typeVariable: NewTypeVariable): Collection<Constraint>

        fun newIncorporatedConstraint(lowerType: UnwrappedType, upperType: UnwrappedType, position: IncorporationConstraintPosition)
    }

    // \alpha is typeVariable, \beta -- other type variable registered in ConstraintStorage
    fun IncorporationContext.incorporate(typeVariable: NewTypeVariable, constraint: Constraint, position: IncorporationConstraintPosition) {
        // we shouldn't incorporate recursive constraint -- It is too dangerous
        if (constraint.type.contains { it.constructor == typeVariable.freshTypeConstructor }) return

        directWithVariable(typeVariable, constraint, position)
        otherInsideMyConstraint(typeVariable, constraint, position)
        insideOtherConstraint(typeVariable, constraint, position)
    }

    // A <:(=) \alpha <:(=) B => A <: B
    private fun IncorporationContext.directWithVariable(typeVariable: NewTypeVariable, constraint: Constraint, position: IncorporationConstraintPosition) {
        // \alpha <: constraint.type
        if (constraint.kind != ConstraintKind.LOWER) {
            getConstraintsForVariable(typeVariable).toMutableList().forEach {
                if (it.kind != ConstraintKind.UPPER) {
                    newIncorporatedConstraint(it.type, constraint.type, position)
                }
            }
        }

        // constraint.type <: \alpha
        if (constraint.kind != ConstraintKind.UPPER) {
            getConstraintsForVariable(typeVariable).toMutableList().forEach {
                if (it.kind != ConstraintKind.LOWER) {
                    newIncorporatedConstraint(constraint.type, it.type, position)
                }
            }
        }
    }

    // \alpha <: Inv<\beta>, \beta <: Number => \alpha <: Inv<out Number>
    private fun IncorporationContext.otherInsideMyConstraint(typeVariable: NewTypeVariable, constraint: Constraint, position: IncorporationConstraintPosition) {
        val otherInMyConstraint = SmartSet.create<NewTypeVariable>()
        constraint.type.contains {
            otherInMyConstraint.addIfNotNull(getTypeVariable(it.constructor))
            false
        }

        for (otherTypeVariable in otherInMyConstraint) {
            // to avoid ConcurrentModificationException
            val otherConstraints = ArrayList(getConstraintsForVariable(otherTypeVariable))
            for (otherConstraint in otherConstraints) {
                generateNewConstraint(typeVariable, constraint, otherTypeVariable, otherConstraint, position)
            }
        }
    }

    // \alpha <: Number, \beta <: Inv<\alpha> => \beta <: Inv<out Number>
    private fun IncorporationContext.insideOtherConstraint(typeVariable: NewTypeVariable, constraint: Constraint, position: IncorporationConstraintPosition) {
        for (typeVariableWithConstraint in allTypeVariablesWithConstraints) {
            val constraintsWhichConstraintMyVariable = typeVariableWithConstraint.constraints.filter {
                it.type.contains { it.constructor == typeVariable.freshTypeConstructor }
            }
            constraintsWhichConstraintMyVariable.forEach {
                generateNewConstraint(typeVariableWithConstraint.typeVariable, it, typeVariable, constraint, position)
            }
        }
    }

    private fun IncorporationContext.generateNewConstraint(
            targetVariable: NewTypeVariable,
            baseConstraint: Constraint,
            otherVariable: NewTypeVariable,
            otherConstraint: Constraint,
            position: IncorporationConstraintPosition
    ) {
        val approximationBounds = when (otherConstraint.kind){
            ConstraintKind.EQUALITY-> {
                val substitutedType = baseConstraint.type.substitute(otherVariable, otherConstraint.type.asTypeProjection())
                ApproximationBounds(substitutedType, substitutedType)
            }
            ConstraintKind.UPPER -> {
                val newCapturedTypeConstructor = NewCapturedTypeConstructor(TypeProjectionImpl(Variance.OUT_VARIANCE, otherConstraint.type),
                                                                            listOf(otherConstraint.type))
                val temporaryCapturedType = NewCapturedType(CaptureStatus.FOR_INCORPORATION,
                                                            newCapturedTypeConstructor,
                                                            lowerType = null)
                baseConstraint.type.substitute(otherVariable, temporaryCapturedType.asTypeProjection()).safeApproximateCapturedTypes {
                    it.captureStatus == CaptureStatus.FOR_INCORPORATION
                }
            }
            ConstraintKind.LOWER -> {
                val newCapturedTypeConstructor = NewCapturedTypeConstructor(TypeProjectionImpl(Variance.IN_VARIANCE, otherConstraint.type),
                                                                            emptyList())
                val temporaryCapturedType = NewCapturedType(CaptureStatus.FOR_INCORPORATION,
                                                            newCapturedTypeConstructor,
                                                            lowerType = otherConstraint.type)
                baseConstraint.type.substitute(otherVariable, temporaryCapturedType.asTypeProjection()).safeApproximateCapturedTypes {
                    it.captureStatus == CaptureStatus.FOR_INCORPORATION
                }
            }
        }

        if (baseConstraint.kind != ConstraintKind.UPPER) {
            newIncorporatedConstraint(approximationBounds.lower, targetVariable.defaultType, position)
        }
        if (baseConstraint.kind != ConstraintKind.LOWER) {
            newIncorporatedConstraint(targetVariable.defaultType, approximationBounds.upper, position)
        }
    }

    private fun UnwrappedType.substitute(typeVariable: NewTypeVariable, value: TypeProjection): UnwrappedType {
        val substitutor = TypeSubstitutor.create(mapOf(typeVariable.freshTypeConstructor to value))
        val type = substitutor.substitute(this, Variance.INVARIANT) ?: error("Impossible to substitute in $this: $typeVariable -> $value")
        return type.unwrap()
    }
}