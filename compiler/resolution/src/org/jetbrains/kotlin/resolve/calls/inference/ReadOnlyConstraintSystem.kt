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

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.resolve.calls.BaseResolvedCall
import org.jetbrains.kotlin.resolve.calls.inference.model.ConstraintStorage
import org.jetbrains.kotlin.resolve.calls.inference.model.VariableWithConstraints
import org.jetbrains.kotlin.resolve.calls.model.*
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.types.*



interface ConstraintSystemBuilder {
    val hasContradiction: Boolean
    val typeVariables: Map<TypeConstructor, VariableWithConstraints>

    // If hasContradiction then this list should contain some diagnostic about problem
    val diagnostics: List<CallDiagnostic>

    fun registerVariable(variable: NewTypeVariable)

    fun addSubtypeConstraint(lowerType: UnwrappedType, upperType: UnwrappedType, position: ConstraintPosition)
    fun addEqualityConstraint(a: UnwrappedType, b: UnwrappedType, position: ConstraintPosition)

    fun addInnerCall(innerCall: BaseResolvedCall.OnlyResolvedCall)
    fun addLambdaArgument(resolvedLambdaArgument: ResolvedLambdaArgument)

    fun addIfIsCompatibleSubtypeConstraint(lowerType: UnwrappedType, upperType: UnwrappedType, position: ConstraintPosition): Boolean




    /**
     * This function removes variables for which we know exact type.
     * @return substitutor from typeVariable to result
     */
    fun simplify(): TypeSubstitutor

    fun build(): ConstraintStorage // return immutable copy of constraint system

    fun startCompletion(): MutableConstraintStorage
}
