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

import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.resolve.calls.BaseResolvedCall
import org.jetbrains.kotlin.resolve.calls.inference.components.ResultTypeResolver
import org.jetbrains.kotlin.resolve.calls.inference.model.Constraint
import org.jetbrains.kotlin.resolve.calls.inference.model.ConstraintStorage
import org.jetbrains.kotlin.resolve.calls.inference.model.VariableWithConstraints
import org.jetbrains.kotlin.resolve.calls.model.CallDiagnostic
import org.jetbrains.kotlin.resolve.calls.model.ResolvedLambdaArgument
import org.jetbrains.kotlin.resolve.calls.model.ThrowableASTCall
import org.jetbrains.kotlin.resolve.calls.results.SimpleConstraintSystem
import org.jetbrains.kotlin.types.TypeConstructor
import org.jetbrains.kotlin.types.TypeConstructorSubstitution
import org.jetbrains.kotlin.types.TypeSubstitutor
import org.jetbrains.kotlin.types.UnwrappedType
import org.jetbrains.kotlin.types.typeUtil.asTypeProjection
import java.util.*

class NewConstraintSystemBuilderImpl(val resultTypeResolver: ResultTypeResolver) : ConstraintSystemBuilder {
    val storage = MutableConstraintStorage()

    override val hasContradiction: Boolean get() = storage.errors.isNotEmpty()
    override val diagnostics: List<CallDiagnostic> get() = storage.errors
    override val typeVariables: Map<TypeConstructor, VariableWithConstraints> get() = storage.notFixedTypeVariables

    override fun registerVariable(variable: NewTypeVariable) = storage.registerVariable(variable)

    override fun addSubtypeConstraint(lowerType: UnwrappedType, upperType: UnwrappedType, position: ConstraintPosition) =
            storage.addSubtypeConstraint(lowerType, upperType, position)

    override fun addEqualityConstraint(a: UnwrappedType, b: UnwrappedType, position: ConstraintPosition) =
            storage.addEqualityConstraint(a, b, position)

    override fun addInnerCall(innerCall: BaseResolvedCall.OnlyResolvedCall) {
        storage.addInnerCall(innerCall)
    }

    override fun addLambdaArgument(resolvedLambdaArgument: ResolvedLambdaArgument) {
        storage.lambdaArguments.add(resolvedLambdaArgument)
    }

    override fun addIfIsCompatibleSubtypeConstraint(lowerType: UnwrappedType, upperType: UnwrappedType, position: ConstraintPosition): Boolean {
        if (hasContradiction) return false
        storage.addSubtypeConstraint(lowerType, upperType, position)
        if (!hasContradiction) return true

        val shouldRemove = { c: Constraint -> c.position === position ||
                                              (c.position is IncorporationConstraintPosition && c.position.from === position)}

        for (variableWithConstraint in storage.notFixedTypeVariables.values) {
            variableWithConstraint.removeLastConstraints(shouldRemove)
        }
        storage.errors.clear()
        storage.initialConstraints.removeAt(storage.initialConstraints.lastIndex)

        return false
    }

    private fun getVariablesForFixation(): Map<NewTypeVariable, UnwrappedType> {
        val fixedVariables = LinkedHashMap<NewTypeVariable, UnwrappedType>()

        for (variableWithConstrains in storage.notFixedTypeVariables.values) {
            val resultType = with(resultTypeResolver) {
                storage.findResultIfThereIsEqualsConstraint(variableWithConstrains, allowedFixToNotProperType = false)
            }
            if (resultType != null) {
                fixedVariables[variableWithConstrains.typeVariable] = resultType
            }
        }
        return fixedVariables
    }

    override fun simplify(): TypeSubstitutor {
        var fixedVariables = getVariablesForFixation()
        while (fixedVariables.isNotEmpty()) {
            for ((variable, resultType) in fixedVariables) {
                storage.fixVariable(variable, resultType)
            }
            fixedVariables = getVariablesForFixation()
        }

        return storage.buildCurrentSubstitutor()
    }

    // todo add some assertions(we should call this method twice, for example. Also we should do not modify anything after this methods)
    override fun build(): ConstraintStorage = storage
    override fun startCompletion(): MutableConstraintStorage = storage
}
