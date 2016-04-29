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

package org.jetbrains.kotlin.types

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.types.KotlinType.StableType.FlexibleType
import org.jetbrains.kotlin.types.KotlinType.StableType.SimpleType
import org.jetbrains.kotlin.types.checker.KotlinTypeChecker
import org.jetbrains.kotlin.types.typeUtil.builtIns
import org.jetbrains.kotlin.types.typeUtil.stableType

// todo move to serialization
interface FlexibleTypeFactory {
    val id: String

    fun create(lowerBound: SimpleType, upperBound: SimpleType): FlexibleType

    object ThrowException : FlexibleTypeFactory {
        private fun error(): Nothing = throw IllegalArgumentException("This factory should not be used.")
        override val id: String
            get() = error()

        override fun create(lowerBound: SimpleType, upperBound: SimpleType): FlexibleType = error()
    }
}

fun KotlinType.isFlexible(): Boolean = stableType is FlexibleType
fun KotlinType.flexibility(): FlexibleType = stableType as FlexibleType
fun KotlinType.asFlexibleType(): FlexibleType? = stableType as? FlexibleType

fun KotlinType.isNullabilityFlexible(): Boolean {
    val flexible = stableType as? FlexibleType ?: return false
    return TypeUtils.isNullableType(flexible.lowerBound) != TypeUtils.isNullableType(flexible.upperBound)
}

// This function is intended primarily for sets: since KotlinType.equals() represents _syntactical_ equality of types,
// whereas KotlinTypeChecker.DEFAULT.equalsTypes() represents semantic equality
// A set of types (e.g. exact bounds etc) may contain, for example, X, X? and X!
// These are not equal syntactically (by KotlinType.equals()), but X! is _compatible_ with others as exact bounds,
// moreover, X! is a better fit.
//
// So, we are looking for a type among this set such that it is equal to all others semantically
// (by KotlinTypeChecker.DEFAULT.equalsTypes()), and fits at least as well as they do.
fun Collection<KotlinType>.singleBestRepresentative(): KotlinType? {
    if (this.size == 1) return this.first()

    return this.firstOrNull {
        candidate ->
        this.all {
            other ->
            // We consider error types equal to anything here, so that intersections like
            // {Array<String>, Array<[ERROR]>} work correctly
            candidate == other || KotlinTypeChecker.ERROR_TYPES_ARE_EQUAL_TO_ANYTHING.equalTypes(candidate, other)
        }
    }
}

fun Collection<TypeProjection>.singleBestRepresentative(): TypeProjection? {
    if (this.size == 1) return this.first()

    val projectionKinds = this.map { it.projectionKind }.toSet()
    if (projectionKinds.size != 1) return null

    val bestType = this.map { it.type }.singleBestRepresentative()
    if (bestType == null) return null

    return TypeProjectionImpl(projectionKinds.single(), bestType)
}

fun KotlinType.lowerIfFlexible(): SimpleType
        = stableType.let { (it as? FlexibleType)?.lowerBound ?: it as SimpleType }
fun KotlinType.upperIfFlexible(): SimpleType
        = stableType.let { (it as? FlexibleType)?.upperBound ?: it as SimpleType }

class FlexibleTypeIml(lowerBound: SimpleType, upperBound: SimpleType) :
        FlexibleType(lowerBound, upperBound) {

    override fun replaceAnnotations(newAnnotations: Annotations) =
        KotlinTypeFactory.createFlexibleType(lowerBound.replaceAnnotations(newAnnotations), upperBound.replaceAnnotations(newAnnotations))

    override fun markNullableAsSpecified(newNullability: Boolean) =
        KotlinTypeFactory.createFlexibleType(lowerBound.markNullableAsSpecified(newNullability),
                                             upperBound.markNullableAsSpecified(newNullability))
}

class DynamicType(builtIns: KotlinBuiltIns,
                  private val annotations: Annotations
): FlexibleType(builtIns.nothingType, builtIns.nullableAnyType) {
    override fun getAnnotations() = annotations

    override val delegate: SimpleType
        get() = upperBound

    override val isMarkedNullable: Boolean
        get() = false

    override fun replaceAnnotations(newAnnotations: Annotations) = DynamicType(upperBound.builtIns, newAnnotations)
    override fun markNullableAsSpecified(newNullability: Boolean) = this
}