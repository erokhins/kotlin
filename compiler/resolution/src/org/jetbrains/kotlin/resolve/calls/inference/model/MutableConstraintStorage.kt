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

package org.jetbrains.kotlin.resolve.calls.inference.model

import org.jetbrains.kotlin.resolve.calls.BaseResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.CallDiagnostic
import org.jetbrains.kotlin.resolve.calls.model.ResolvedLambdaArgument
import org.jetbrains.kotlin.types.TypeConstructor
import org.jetbrains.kotlin.types.UnwrappedType
import java.util.*


class MutableVariableWithConstraints(
        override val typeVariable: NewTypeVariable,
        constraints: Collection<Constraint> = emptyList()
) : VariableWithConstraints {
    override val constraints: List<Constraint> get() = mutableConstraints
    private val mutableConstraints = MyArrayList(constraints)

    // return true, if this constraint is new
    fun addConstraint(constraint: Constraint): Boolean {
        if (constraints.any {
            it.typeHashCode == constraint.typeHashCode && newConstraintIsUseless(it.kind, constraint.kind) && it.type == constraint.type
        }) {
            return false
        }

        mutableConstraints.add(constraint)
        return true
    }

    fun removeLastConstraints(shouldRemove: (Constraint) -> Boolean) {
        mutableConstraints.removeLast(shouldRemove)
    }

    // todo optimize it!
    fun removeConstrains(shouldRemove: (Constraint) -> Boolean) {
        val newConstraints = mutableConstraints.filter { !shouldRemove(it) }
        mutableConstraints.clear()
        mutableConstraints.addAll(newConstraints)
    }

    private fun newConstraintIsUseless(oldKind: ConstraintKind, newKind: ConstraintKind) =
            when (oldKind) {
                ConstraintKind.EQUALITY -> true
                ConstraintKind.LOWER -> newKind == ConstraintKind.LOWER
                ConstraintKind.UPPER -> newKind == ConstraintKind.UPPER
            }

    private class MyArrayList<E>(c: Collection<E>): ArrayList<E>(c) {
        fun removeLast(predicate: (E) -> Boolean) {
            val newSize = indexOfLast { !predicate(it) } + 1

            if (newSize != size) {
                removeRange(newSize, size)
            }
        }
    }
}

// todo may be we should use LinkedHasMap
class MutableConstraintStorage : ConstraintStorage {
    override val allTypeVariables: MutableMap<TypeConstructor, NewTypeVariable> = HashMap()
    override val notFixedTypeVariables: MutableMap<TypeConstructor, MutableVariableWithConstraints> = HashMap()
    override val initialConstraints: MutableList<InitialConstraint> = ArrayList()
    override var maxTypeDepthFromInitialConstraints: Int = 1
    override val errors: MutableList<CallDiagnostic> = ArrayList()
    override val fixedTypeVariables: MutableMap<TypeConstructor, UnwrappedType> = HashMap()
    override val lambdaArguments: MutableList<ResolvedLambdaArgument> = ArrayList()
    override val innerCalls: MutableList<BaseResolvedCall.OnlyResolvedCall> = ArrayList()
}