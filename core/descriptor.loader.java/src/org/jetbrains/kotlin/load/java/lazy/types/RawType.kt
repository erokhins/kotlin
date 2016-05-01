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

package org.jetbrains.kotlin.load.java.lazy.types

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.load.java.components.TypeUsage
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.KotlinType.StableType.SimpleType

class RawTypeImpl(lowerBound: SimpleType, upperBound: SimpleType) :
        KotlinType.StableType.FlexibleType(lowerBound, upperBound), RawType {
    override val substitution: TypeSubstitution?
        get() = RawSubstitution
    override val substitutionToComposeWith: TypeSubstitution?
        get() = RawSubstitution

    private fun DescriptorRenderer.renderArguments(jetType: KotlinType) = jetType.arguments.map { renderTypeProjection(it) }

    private fun String.replaceArgs(newArgs: String): String {
        if (!contains('<')) return this
        return "${substringBefore('<')}<$newArgs>${substringAfterLast('>')}"
    }

    override fun renderInflexible(type: KotlinType, renderer: DescriptorRenderer): String? {
        if (type.arguments.isNotEmpty()) return null

        return buildString {
            append(renderer.renderTypeConstructor(type.constructor))
            append("(raw)")
            if (type.isMarkedNullable) append('?')
        }
    }

    override fun renderBounds(flexibleType: FlexibleType, renderer: DescriptorRenderer): Pair<String, String>? {
        val lowerArgs = renderer.renderArguments(flexibleType.lowerBound)
        val upperArgs = renderer.renderArguments(flexibleType.upperBound)

        val lowerRendered = renderer.renderType(flexibleType.lowerBound)
        val upperRendered = renderer.renderType(flexibleType.upperBound)

        if (!upperArgs.isNotEmpty()) return null

        val newArgs = lowerArgs.map { "(raw) $it" }.joinToString(", ")
        val newUpper =
                if (lowerArgs.zip(upperArgs).all { onlyOutDiffers(it.first, it.second) })
                    upperRendered.replaceArgs(newArgs)
                else upperRendered
        return Pair(lowerRendered.replaceArgs(newArgs), newUpper)
    }

    private fun onlyOutDiffers(first: String, second: String) = first == second.removePrefix("out ") || second == "*"

    override fun replaceAnnotations(newAnnotations: Annotations): KotlinType
            = RawTypeImpl(lowerBound.replaceAnnotations(newAnnotations), upperBound.replaceAnnotations(newAnnotations))

    override fun markNullableAsSpecified(newNullability: Boolean): KotlinType
            = RawTypeImpl(lowerBound.markNullableAsSpecified(newNullability), upperBound.markNullableAsSpecified(newNullability))
}

internal object RawSubstitution : TypeSubstitution() {
    override fun get(key: KotlinType) = TypeProjectionImpl(eraseType(key))

    private val lowerTypeAttr = TypeUsage.MEMBER_SIGNATURE_INVARIANT.toAttributes().toFlexible(JavaTypeFlexibility.FLEXIBLE_LOWER_BOUND)
    private val upperTypeAttr = TypeUsage.MEMBER_SIGNATURE_INVARIANT.toAttributes().toFlexible(JavaTypeFlexibility.FLEXIBLE_UPPER_BOUND)

    fun eraseType(type: KotlinType): KotlinType {
        val declaration = type.constructor.declarationDescriptor
        return when (declaration) {
            is TypeParameterDescriptor -> eraseType(declaration.getErasedUpperBound())
            is ClassDescriptor -> {
                val (lower, isRawL) = eraseInflexibleBasedOnClassDescriptor(type.lowerIfFlexible(), declaration, lowerTypeAttr)
                val (upper, isRawU) = eraseInflexibleBasedOnClassDescriptor(type.lowerIfFlexible(), declaration, upperTypeAttr)

                if (isRawL || isRawU) {
                    RawTypeImpl(lower, upper)
                }
                else {
                    KotlinTypeFactory.createFlexibleType(lower, upper)
                }
            }
            else -> error("Unexpected declaration kind: $declaration")
        }
    }

    private fun eraseInflexibleBasedOnClassDescriptor(
            type: SimpleType, declaration: ClassDescriptor, attr: JavaTypeAttributes
    ): Pair<SimpleType, Boolean> {
        if (type.constructor.parameters.isEmpty()) return type to false

        if (KotlinBuiltIns.isArray(type)) {
            val componentTypeProjection = type.arguments[0]
            val arguments = listOf(
                    TypeProjectionImpl(componentTypeProjection.projectionKind, eraseType(componentTypeProjection.type))
            )
            return KotlinTypeFactory.create(
                    type.annotations, type.constructor, type.isMarkedNullable, arguments,
                    declaration.getMemberScope(arguments)
            ) to false
        }

        if (type.isError) return ErrorUtils.createErrorType("Raw error type: ${type.constructor}") to false

        val constructor = type.constructor
        return KotlinTypeFactory.create(
                type.annotations, constructor, type.isMarkedNullable,
                type.constructor.parameters.map {
                    parameter ->
                    computeProjection(parameter, attr)
                },
                declaration.getMemberScope(RawSubstitution)) to true
    }

    fun computeProjection(
            parameter: TypeParameterDescriptor,
            attr: JavaTypeAttributes,
            erasedUpperBound: KotlinType = parameter.getErasedUpperBound()
    ) = when (attr.flexibility) {
        // Raw(List<T>) => (List<Any?>..List<*>)
        // Raw(Enum<T>) => (Enum<Enum<*>>..Enum<out Enum<*>>)
        // In the last case upper bound is equal to star projection `Enum<*>`,
        // but we want to keep matching tree structure of flexible bounds (at least they should have the same size)
        JavaTypeFlexibility.FLEXIBLE_LOWER_BOUND -> TypeProjectionImpl(
                // T : String -> String
                // in T : String -> String
                // T : Enum<T> -> Enum<*>
                Variance.INVARIANT, erasedUpperBound
        )
        JavaTypeFlexibility.FLEXIBLE_UPPER_BOUND, JavaTypeFlexibility.INFLEXIBLE -> {
            if (!parameter.variance.allowsOutPosition)
                // in T -> Comparable<Nothing>
                TypeProjectionImpl(Variance.INVARIANT, parameter.builtIns.nothingType)
            else if (erasedUpperBound.constructor.parameters.isNotEmpty())
                // T : Enum<E> -> out Enum<*>
                TypeProjectionImpl(Variance.OUT_VARIANCE, erasedUpperBound)
            else
                // T : String -> *
                makeStarProjection(parameter, attr)
        }
    }

    override fun isEmpty() = false
}
