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

import org.jetbrains.kotlin.resolve.calls.inference.components.TypeCheckerContextForConstraintSystem
import org.jetbrains.kotlin.resolve.calls.inference.model.*
import org.jetbrains.kotlin.resolve.calls.model.CallDiagnostic
import org.jetbrains.kotlin.types.FlexibleType
import org.jetbrains.kotlin.types.SimpleType
import org.jetbrains.kotlin.types.TypeConstructor
import org.jetbrains.kotlin.types.UnwrappedType
import org.jetbrains.kotlin.types.checker.CaptureStatus
import org.jetbrains.kotlin.types.checker.NewCapturedType
import org.jetbrains.kotlin.types.checker.NewKotlinTypeChecker
import org.jetbrains.kotlin.types.typeUtil.contains
import java.util.*

class ConstraintInjector(val constraintIncorporator: ConstraintIncorporator) {
    private val ALLOWED_DEPTH_DELTA_FOR_INCORPORATION = 3

    interface Context {
        val allTypeVariables: Map<TypeConstructor, NewTypeVariable>

        fun getConstraintsForVariable()

        var maxTypeDepthFromInitialConstraints: Int
        val initialConstraints: MutableList<InitialConstraint>
        val errors: MutableList<CallDiagnostic>

        // true if constraint was added
        fun addConstraint(typeVariable: NewTypeVariable, constraint: Constraint): Boolean
    }

    fun addInitialSubtypeConstraint(c: Context, lowerType: UnwrappedType, upperType: UnwrappedType, position: ConstraintPosition) {
        c.initialConstraints.add(InitialConstraint(lowerType, upperType, ConstraintKind.UPPER, position))
        updateAllowedTypeDepth(c, lowerType)
        updateAllowedTypeDepth(c, upperType)
        addSubTypeConstraint(c, lowerType, upperType, position)
    }

    fun addInitialEqualityConstraint(c: Context, a: UnwrappedType, b: UnwrappedType, position: ConstraintPosition) {
        c.initialConstraints.add(InitialConstraint(a, b, ConstraintKind.EQUALITY, position))
        updateAllowedTypeDepth(c, a)
        updateAllowedTypeDepth(c, b)
        addSubTypeConstraint(c, a, b, position)
        addSubTypeConstraint(c, b, a, position)
    }


    private fun addSubTypeConstraint(c: Context, lowerType: UnwrappedType, upperType: UnwrappedType, position: ConstraintPosition) {
        val typeCheckerContext = TypeCheckerContext(c, position)
        with(NewKotlinTypeChecker) {
            if (!typeCheckerContext.isSubtypeOf(lowerType, upperType)) {
                c.errors.add(NewConstraintError(lowerType, upperType, position))
            }
        }
    }

    private fun updateAllowedTypeDepth(c: Context, initialType: UnwrappedType) {
        c.maxTypeDepthFromInitialConstraints = Math.max(c.maxTypeDepthFromInitialConstraints, initialType.typeDepth())
    }

    private fun UnwrappedType.typeDepth() =
            when (this) {
                is SimpleType -> typeDepth()
                is FlexibleType -> Math.max(lowerBound.typeDepth(), upperBound.typeDepth())
            }

    private fun SimpleType.typeDepth(): Int {
        val maxInArguments = arguments.asSequence().map {
            if (it.isStarProjection) 1 else it.type.unwrap().typeDepth()
        }.max() ?: 0

        return maxInArguments + 1
    }

    private inner class TypeCheckerContext(
            val c: Context,
            val position: ConstraintPosition
    ) : TypeCheckerContextForConstraintSystem(), ConstraintIncorporator.Context {
        override fun isMyTypeVariable(type: SimpleType): Boolean = c.allTypeVariables.containsKey(type.constructor)
        override fun addUpperConstraint(typeVariable: TypeConstructor, superType: UnwrappedType) =
                addConstraint(typeVariable, superType, ConstraintKind.UPPER)

        override fun addLowerConstraint(typeVariable: TypeConstructor, subType: UnwrappedType) =
                addConstraint(typeVariable, subType, ConstraintKind.LOWER)

        fun addConstraint(typeVariableConstructor: TypeConstructor, type: UnwrappedType, kind: ConstraintKind) {
            val typeVariable = c.allTypeVariables[typeVariableConstructor]
                               ?: error("Should by type variableConstructor: $typeVariableConstructor. ${c.allTypeVariables.values}")

            if (type.contains { val captureStatus = (it as? NewCapturedType)?.captureStatus
                assert(captureStatus != CaptureStatus.FOR_INCORPORATION) {
                    "Captured type for incorporation shouldn't escape from incorporation: $type"
                }
                captureStatus != null && captureStatus != CaptureStatus.FROM_EXPRESSION
            }) {
                c.errors.add(CapturedTypeFromSubtyping(typeVariable, type, position))
                return
            }

            if (type.typeDepth() > c.maxTypeDepthFromInitialConstraints) return

            val newConstraint = Constraint(kind, type, position)
            if (c.addConstraint(typeVariable, newConstraint)) {
                // todo do not create IncorporationConstraintPosition if we already have IncorporationConstraintPosition
                constraintIncorporator.incorporate(this, typeVariable, newConstraint, IncorporationConstraintPosition(position))
            }
        }

        override fun addNewIncorporatedConstraint(lowerType: UnwrappedType, upperType: UnwrappedType, position: IncorporationConstraintPosition) {
            val allowedTypeDepth = c.maxTypeDepthFromInitialConstraints + ALLOWED_DEPTH_DELTA_FOR_INCORPORATION
            if (lowerType.typeDepth() > allowedTypeDepth || upperType.typeDepth() > allowedTypeDepth) return
            addSubTypeConstraint(c, lowerType, upperType, position)
        }

        override val allTypeVariablesWithConstraints: Collection<VariableWithConstraints>
            get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

        override fun getTypeVariable(typeConstructor: TypeConstructor): NewTypeVariable? {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getConstraintsForVariable(typeVariable: NewTypeVariable): Collection<Constraint> {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

    }
}