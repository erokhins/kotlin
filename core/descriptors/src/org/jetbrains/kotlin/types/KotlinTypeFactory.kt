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
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.types.KotlinType.StableType.SimpleType

object KotlinTypeFactory {
    @JvmStatic @JvmOverloads
    fun createDynamicType(
            builtIns: KotlinBuiltIns,
            annotations: Annotations = Annotations.EMPTY
    ) = DynamicType(builtIns, annotations)

    @JvmStatic
    fun createFlexibleType(
            lowerBound: SimpleType,
            upperBound: SimpleType
    ): KotlinType.StableType<*> {
        if (lowerBound == upperBound) return lowerBound

        return FlexibleTypeIml(lowerBound, upperBound)
    }

    @JvmStatic fun create(
            annotations: Annotations,
            constructor: TypeConstructor,
            nullable: Boolean,
            arguments: List<TypeProjection>,
            memberScope: MemberScope
    ): SimpleType = SimpleTypeImpl(annotations, constructor, arguments, nullable, memberScope)

    @JvmStatic fun create(
            annotations: Annotations,
            descriptor: ClassDescriptor,
            nullable: Boolean,
            arguments: List<TypeProjection>
    ): SimpleType = SimpleTypeImpl(annotations, descriptor.typeConstructor, arguments, nullable, descriptor.getMemberScope(arguments))

    @JvmStatic fun createSimpleType(
            baseType: SimpleType,
            annotations: Annotations = baseType.annotations,
            constructor: TypeConstructor = baseType.constructor,
            nullable: Boolean = baseType.isMarkedNullable,
            arguments: List<TypeProjection> = baseType.arguments,
            memberScope: MemberScope = baseType.memberScope
    ): SimpleType = create(annotations, constructor, nullable, arguments, memberScope)
}


abstract class AbstractSimpleType : SimpleType() {
    override fun replaceAnnotations(newAnnotations: Annotations): SimpleType {
        if (newAnnotations === annotations) return this
        return KotlinTypeFactory.createSimpleType(this, annotations = newAnnotations)
    }

    override fun markNullableAsSpecified(newNullability: Boolean): SimpleType
            = KotlinTypeFactory.createSimpleType(this, nullable = newNullability)
}

internal final class SimpleTypeImpl(
        private val annotations: Annotations,
        override val constructor: TypeConstructor,
        override val arguments: List<TypeProjection>,
        override val isMarkedNullable: Boolean,
        override val memberScope: MemberScope
) : AbstractSimpleType() {
    override fun getAnnotations(): Annotations = annotations

    init {
        if (memberScope is ErrorUtils.ErrorScope) {
            throw IllegalStateException("JetTypeImpl should not be created for error type: $memberScope\n$constructor")
        }
    }
}