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

import org.jetbrains.kotlin.resolve.calls.inference.model.Constraint
import org.jetbrains.kotlin.resolve.calls.inference.model.ConstraintKind
import org.jetbrains.kotlin.resolve.calls.inference.model.ResolveDirection
import org.jetbrains.kotlin.resolve.calls.model.LambdaArgument
import org.jetbrains.kotlin.resolve.calls.model.ResolvedLambdaArgument
import org.jetbrains.kotlin.types.FlexibleType
import org.jetbrains.kotlin.types.SimpleType
import org.jetbrains.kotlin.types.UnwrappedType
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.types.checker.NewKotlinTypeChecker
import org.jetbrains.kotlin.types.checker.isIntersectionType
import org.jetbrains.kotlin.utils.DFS
import org.jetbrains.kotlin.utils.SmartList
import java.util.*

data class NodeWithDirection(val variable: NewTypeVariable, val direction: ResolveDirection)

fun computeCompletionOrder(
        constraintStorage: MutableConstraintStorage,
        topReturnType: UnwrappedType
): List<NodeWithDirection> = SystemCompleter(constraintStorage, constraintStorage.lambdaArguments.associateBy { it.argument }).getCompletionOrder(topReturnType)

private class SystemCompleter(
        val constraintStorage: MutableConstraintStorage,
        // order of lambda arguments is essential
        val allLambdaArguments: Map<LambdaArgument, ResolvedLambdaArgument>
) {
    private val directions = HashMap<NewTypeVariable, ResolveDirection>()

    // first in the list -- first fix
    fun getCompletionOrder(topReturnType: UnwrappedType): List<NodeWithDirection> {
        setupDirections(topReturnType)

        return topologicalOrderWith0Priority().map { NodeWithDirection(it, directions[it] ?: ResolveDirection.UNKNOWN) }
    }

    private fun topologicalOrderWith0Priority(): List<NewTypeVariable> {
        val handler = object : DFS.CollectingNodeHandler<NewTypeVariable, NewTypeVariable, LinkedHashSet<NewTypeVariable>>(LinkedHashSet()) {
            override fun afterChildren(current: NewTypeVariable) {
                // we have guaranty that from end of 0 edge there is no other edges with priority 0
                result.addAll(get0Edges(current))

                result.add(current)
            }
        }

        for (typeVariableWithConstraints in constraintStorage.allTypeVariablesWithConstraints) {
            DFS.doDfs(typeVariableWithConstraints.typeVariable, DFS.Neighbors(this::getEdges), DFS.VisitedWithSet<NewTypeVariable>(), handler)
        }
        return handler.result().toList()
    }


    private fun setupDirections(topReturnType: UnwrappedType) {
        topReturnType.visitType(ResolveDirection.TO_SUBTYPE) { variable, direction ->
            enterToNode(variable, direction)
        }
        for (resolvedLambdaArgument in allLambdaArguments.values) {
            inner@ for (typeVariable in resolvedLambdaArgument.myTypeVariables) {
                if (typeVariable.kind == LambdaNewTypeVariable.Kind.RETURN_TYPE) continue@inner

                enterToNode(typeVariable, ResolveDirection.TO_SUBTYPE)
            }
        }
    }

    private fun enterToNode(variable: NewTypeVariable, direction: ResolveDirection) {
        if (direction == ResolveDirection.UNKNOWN) return

        val previous = directions[variable]
        if (previous != null) {
            if (previous != direction) {
                directions[variable] = ResolveDirection.UNKNOWN
            }
            return
        }

        directions[variable] = direction

        for ((otherVariable, otherDirection) in get12Edges(variable, direction)) {
            enterToNode(otherVariable, otherDirection)
        }
    }

    private fun getEdges(variable: NewTypeVariable): List<NewTypeVariable> {
        val direction = directions[variable] ?: ResolveDirection.UNKNOWN
        return get12Edges(variable, direction).map(NodeWithDirection::variable) + get0Edges(variable)
    }

    /**
     * Now we use only priority 0 and {1, 2}.
     * Current vision of edge priority for type variable \alpha to variable \beta:
     *      0 -- { \beta -> \alpha } i.e. return type depend of all parameters types of lambda
     *      1 -- \alpha <: Inv<\beta> or \alpha >: Pair<Inv<\beta & Any>, Int> ot \alpha <: \beta & Any
     *      2 -- \alpha <: \beta or \alpha >: \beta?
     */
    private fun get12Edges(variable: NewTypeVariable, direction: ResolveDirection, include2: Boolean = true): List<NodeWithDirection> {
        fun isInterestingConstraint(direction: ResolveDirection, constraint: Constraint): Boolean {
            return (direction == ResolveDirection.TO_SUBTYPE && constraint.kind == ConstraintKind.UPPER) ||
                   (direction == ResolveDirection.TO_SUPERTYPE && constraint.kind == ConstraintKind.LOWER)
        }

        val result = SmartList<NodeWithDirection>()

        for (constraint in constraintStorage.getConstraintsForVariable(variable)) {
            if (!isInterestingConstraint(direction, constraint)) continue

            if (include2 || constraintStorage.getTypeVariable(constraint.type.constructor) == null) { // because we collect only type 1 of edges
                constraint.type.visitType(direction) { variable, direction ->
                    result.add(NodeWithDirection(variable, direction))
                }
            }
        }

        return result
    }

    private fun get0Edges(variable: NewTypeVariable): List<NewTypeVariable> {
        if (variable !is LambdaNewTypeVariable || variable.kind != LambdaNewTypeVariable.Kind.RETURN_TYPE) return emptyList()

        val resolvedLambdaArgument = allLambdaArguments[variable.lambdaArgument] ?:
                                     error("Missing resolved lambda argument for ${variable.lambdaArgument}")

        return resolvedLambdaArgument.myTypeVariables.filter { it.kind != LambdaNewTypeVariable.Kind.RETURN_TYPE }
    }


    private fun UnwrappedType.visitType(startDirection: ResolveDirection, action: (typeVariable: NewTypeVariable, direction: ResolveDirection) -> Unit) =
            when (this) {
                is SimpleType -> visitType(startDirection, action)
                is FlexibleType -> {
                    lowerBound.visitType(startDirection, action)
                    upperBound.visitType(startDirection, action)
                }
            }

    private fun SimpleType.visitType(startDirection: ResolveDirection, action: (typeVariable: NewTypeVariable, direction: ResolveDirection) -> Unit) {
        if (isIntersectionType) {
            constructor.supertypes.forEach {
                it.unwrap().visitType(startDirection, action)
            }
            return
        }

        if (arguments.isEmpty()) {
            constraintStorage.getTypeVariable(constructor)?.let {
                action(it, startDirection)
            }
            return
        }

        val parameters = constructor.parameters
        if (parameters.size != arguments.size) return // incorrect type

        fun ResolveDirection.opposite() = when (this) {
            ResolveDirection.UNKNOWN -> ResolveDirection.UNKNOWN
            ResolveDirection.TO_SUPERTYPE -> ResolveDirection.TO_SUBTYPE
            ResolveDirection.TO_SUBTYPE -> ResolveDirection.TO_SUPERTYPE
        }

        for ((argument, parameter) in arguments.zip(parameters)) {
            if (argument.isStarProjection) continue

            val variance = NewKotlinTypeChecker.effectiveVariance(parameter.variance, argument.projectionKind) ?: Variance.INVARIANT
            val innerDirection = when (variance) {
                Variance.INVARIANT -> ResolveDirection.UNKNOWN
                Variance.OUT_VARIANCE -> startDirection
                Variance.IN_VARIANCE -> startDirection.opposite()
            }

            argument.type.unwrap().visitType(innerDirection, action)
        }
    }
}