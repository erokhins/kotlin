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

import org.jetbrains.kotlin.resolve.calls.BaseResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.CallDiagnostic
import org.jetbrains.kotlin.resolve.calls.model.DiagnosticReporter
import org.jetbrains.kotlin.resolve.calls.model.ResolvedLambdaArgument
import org.jetbrains.kotlin.resolve.calls.tower.ResolutionCandidateApplicability
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.checker.CaptureStatus
import org.jetbrains.kotlin.types.checker.NewCapturedType
import org.jetbrains.kotlin.types.checker.NewKotlinTypeChecker
import org.jetbrains.kotlin.types.typeUtil.contains
import java.util.*

enum class ConstraintKind {
    LOWER,
    UPPER,
    EQUALITY
}

enum class ResolveDirection {
    TO_SUBTYPE,
    TO_SUPERTYPE,
    UNKNOWN
}



class Constraint(
        val kind: ConstraintKind,
        val type: UnwrappedType, // flexible types here is allowed
        val position: ConstraintPosition,
        val typeHashCode: Int = type.hashCode()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as Constraint

        if (typeHashCode != other.typeHashCode) return false
        if (kind != other.kind) return false
        if (type != other.type) return false
        if (position != other.position) return false

        return true
    }

    override fun hashCode() = typeHashCode
}

interface VariableWithConstraints {
    val typeVariable: NewTypeVariable
    val constraints: List<Constraint>
}

class MutableVariableWithConstraints(
        override val typeVariable: NewTypeVariable,
        constraints: Collection<Constraint> = emptyList()
) : VariableWithConstraints {
    override val constraints: List<Constraint> get() = mutableConstraints
    private val mutableConstraints = MyArrayList(constraints)

    // return constraint, if this constraint is new
    fun addConstraint(constraintKind: ConstraintKind, type: UnwrappedType, position: ConstraintPosition): Constraint? {
        val typeHashCode = type.hashCode()
        val previousConstraints = constraintsWithType(typeHashCode, type)
        if (previousConstraints.any { newConstraintIsUseless(it.kind, constraintKind) }) {
            return null
        }

        val constraint = Constraint(constraintKind, type, position, typeHashCode)
        mutableConstraints.add(constraint)
        return constraint
    }

    fun removeLastConstraints(shouldRemove: (Constraint) -> Boolean) {
        mutableConstraints.removeLast(shouldRemove)
    }

    private fun newConstraintIsUseless(oldKind: ConstraintKind, newKind: ConstraintKind) =
            when (oldKind) {
                ConstraintKind.EQUALITY -> true
                ConstraintKind.LOWER -> newKind == ConstraintKind.LOWER
                ConstraintKind.UPPER -> newKind == ConstraintKind.UPPER
            }

    private fun constraintsWithType(typeHashCode: Int, type: UnwrappedType) =
            constraints.filter { it.typeHashCode == typeHashCode && it.type == type }

    private class MyArrayList<E>(c: Collection<E>): ArrayList<E>(c) {
        fun removeLast(predicate: (E) -> Boolean) {
            val newSize = indexOfLast { !predicate(it) } + 1

            if (newSize != size) {
                removeRange(newSize, size)
            }
        }
    }
}

class InitialConstraint(
        val subType: UnwrappedType,
        val superType: UnwrappedType,
        val constraintKind: ConstraintKind,
        val position: ConstraintPosition
)

private const val ALLOWED_DEPTH_DELTA_FOR_INCORPORATION = 3

class NewConstraintError(val lowerType: UnwrappedType, val upperType: UnwrappedType, val position: ConstraintPosition):
        CallDiagnostic(ResolutionCandidateApplicability.INAPPLICABLE) {
    override fun report(reporter: DiagnosticReporter) = TODO() // todo
}
class CapturedTypeFromSubtyping(val typeVariable: NewTypeVariable, val constraintType: UnwrappedType, val position: ConstraintPosition) :
        CallDiagnostic(ResolutionCandidateApplicability.INAPPLICABLE) {
    override fun report(reporter: DiagnosticReporter) = TODO() // todo
}
class NotEnoughInformationForTypeParameter(val typeVariable: NewTypeVariable) : CallDiagnostic(ResolutionCandidateApplicability.INAPPLICABLE) {
    override fun report(reporter: DiagnosticReporter) = TODO("not implemented") // todo
}

class ConstraintStorage : ConstraintIncorporator.IncorporationContext, ConstraintFixator.FixationContext, ReadOnlyConstraintSystem {
    override val allTypeVariables: MutableMap<TypeConstructor, NewTypeVariable> = HashMap()
    override val notFixedTypeVariables: MutableMap<TypeConstructor, MutableVariableWithConstraints> = HashMap()
    override val initialConstraints: MutableList<InitialConstraint> = ArrayList()
    override var allowedTypeDepth: Int = 1 + ALLOWED_DEPTH_DELTA_FOR_INCORPORATION
    override val errors: MutableList<CallDiagnostic> = ArrayList()
    override val fixedTypeVariables: MutableMap<TypeConstructor, UnwrappedType> = HashMap()
    override val lambdaArguments: MutableList<ResolvedLambdaArgument> = ArrayList()
    override val innerCalls: MutableList<BaseResolvedCall.OnlyResolvedCall> = ArrayList()

    private fun updateAllowedTypeDepth(initialType: UnwrappedType) {
        allowedTypeDepth = Math.max(allowedTypeDepth, initialType.typeDepth() + ALLOWED_DEPTH_DELTA_FOR_INCORPORATION)
    }

    fun registerVariable(variable: NewTypeVariable) {
        assert(!allTypeVariables.contains(variable.freshTypeConstructor)) {
            "Already registered: $variable"
        }
        allTypeVariables[variable.freshTypeConstructor] = variable
        notFixedTypeVariables[variable.freshTypeConstructor] = MutableVariableWithConstraints(variable)
    }

    fun addInnerCall(innerCall: BaseResolvedCall.OnlyResolvedCall) {
        addSubsystem(innerCall.candidate.lastCall.csBuilder.build())
        innerCalls.add(innerCall)
    }

    private fun addSubsystem(otherSystem: ReadOnlyConstraintSystem) {
        for ((variable, constraints) in otherSystem.notFixedTypeVariables) {
            notFixedTypeVariables[variable] = MutableVariableWithConstraints(constraints.typeVariable, constraints.constraints)
        }
        initialConstraints.addAll(otherSystem.initialConstraints)
        allowedTypeDepth = Math.max(allowedTypeDepth, otherSystem.allowedTypeDepth)

        // todo may be we should check instead that otherSystem.errors is empty
        errors.addAll(otherSystem.errors)
        fixedTypeVariables.putAll(otherSystem.fixedTypeVariables)
        lambdaArguments.addAll(otherSystem.lambdaArguments)
        allTypeVariables.putAll(otherSystem.allTypeVariables)
    }

    override val allTypeVariablesWithConstraints: Collection<MutableVariableWithConstraints>
        get() = notFixedTypeVariables.values

    override fun getConstraintsForVariable(typeVariable: NewTypeVariable): Collection<Constraint> {
        val variableWithConstraints = notFixedTypeVariables[typeVariable.freshTypeConstructor]
                                      ?: error("This is not my type variable: $typeVariable! My type variables: ${notFixedTypeVariables.keys}")
        return variableWithConstraints.constraints
    }

    override fun getTypeVariable(typeConstructor: TypeConstructor): NewTypeVariable? = allTypeVariables[typeConstructor]

    override fun newIncorporatedConstraint(lowerType: UnwrappedType, upperType: UnwrappedType, position: IncorporationConstraintPosition) =
            newConstraint(lowerType, upperType, position)

    override fun isProperType(type: UnwrappedType) =
            !type.contains {
                it is SimpleType && allTypeVariables.containsKey(it.constructor)
            }


    fun addSubtypeConstraint(lowerType: UnwrappedType, upperType: UnwrappedType, position: ConstraintPosition) {
        initialConstraints.add(InitialConstraint(lowerType, upperType, ConstraintKind.LOWER, position))
        updateAllowedTypeDepth(lowerType)
        updateAllowedTypeDepth(upperType)
        newConstraint(lowerType, upperType, position)
    }

    fun addEqualityConstraint(a: UnwrappedType, b: UnwrappedType, position: ConstraintPosition) {
        initialConstraints.add(InitialConstraint(a, b, ConstraintKind.EQUALITY, position))
        updateAllowedTypeDepth(a)
        updateAllowedTypeDepth(b)
        newConstraint(a, b, position)
        newConstraint(b, a, position)
    }

    internal fun newConstraint(lowerType: UnwrappedType, upperType: UnwrappedType, position: ConstraintPosition) {
        val typeCheckerContext = TypeCheckerContext(position)
        with(NewKotlinTypeChecker) {
            if (!typeCheckerContext.isSubtypeOf(lowerType, upperType)) {
                errors.add(NewConstraintError(lowerType, upperType, position))
            }
        }
    }

    fun incorporateNewConstraint(typeVariable: NewTypeVariable, constraint: Constraint, position: ConstraintPosition) {
        if (constraint.type.typeDepth() > allowedTypeDepth) return

        val newPosition = if (position is IncorporationConstraintPosition) position else IncorporationConstraintPosition(position)

        with(ConstraintIncorporator) {
            incorporate(typeVariable, constraint, newPosition)
        }
    }

    // todo: maybe there is some constraint which we can simplify.
    // for example, we can delete all constraints that contain fixed types
    fun fixVariable(variable: NewTypeVariable, resultType: UnwrappedType) {
        addEqualityConstraint(variable.defaultType, resultType, FixVariableConstraintPosition(variable))
        fixedTypeVariables[variable.freshTypeConstructor] = resultType
        notFixedTypeVariables.remove(variable.freshTypeConstructor)
    }


    inner class TypeCheckerContext(val position: ConstraintPosition) : TypeCheckerContextForConstraintSystem() {
        override fun isMyTypeVariable(type: SimpleType): Boolean = allTypeVariables.containsKey(type.constructor)
        override fun addUpperConstraint(typeVariable: TypeConstructor, superType: UnwrappedType) =
                addConstraint(typeVariable, superType, ConstraintKind.UPPER)

        override fun addLowerConstraint(typeVariable: TypeConstructor, subType: UnwrappedType) =
                addConstraint(typeVariable, subType, ConstraintKind.LOWER)

        fun addConstraint(typeVariable: TypeConstructor, type: UnwrappedType, kind: ConstraintKind) {
            val variableWithConstrains = notFixedTypeVariables[typeVariable] ?: error("Should by type variable: $typeVariable. ${notFixedTypeVariables.keys}")

            if (type.contains { val captureStatus = (it as? NewCapturedType)?.captureStatus
                assert(captureStatus != CaptureStatus.FOR_INCORPORATION) {
                    "Captured type for incorporation shouldn't escape from incorporation: $type"
                }
                captureStatus != null && captureStatus != CaptureStatus.FROM_EXPRESSION
            }) {
                errors.add(CapturedTypeFromSubtyping(variableWithConstrains.typeVariable, type, position))
                return
            }

            if (type.typeDepth() > allowedTypeDepth) return

            val addedConstraint = variableWithConstrains.addConstraint(kind, type, position) ?: return

            incorporateNewConstraint(variableWithConstrains.typeVariable, addedConstraint, position)
        }
    }

}

fun UnwrappedType.typeDepth() =
    when (this) {
        is SimpleType -> typeDepth()
        is FlexibleType -> Math.max(lowerBound.typeDepth(), upperBound.typeDepth())
    }

fun SimpleType.typeDepth(): Int {
    val maxInArguments = arguments.asSequence().map {
        if (it.isStarProjection) 1 else it.type.unwrap().typeDepth()
    }.max() ?: 0

    return maxInArguments + 1
}