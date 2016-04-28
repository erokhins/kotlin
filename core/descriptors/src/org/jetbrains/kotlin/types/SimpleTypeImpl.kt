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

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.types.KotlinType.SimpleType

fun KotlinType.markNullableAsSpecified(nullable: Boolean) = transform({ markNullableAsSpecified(nullable) }) {
    // Nullability has no effect on dynamics
    if (isDynamic()) return@transform this

    KotlinType.FlexibleType(lowerBound.markNullableAsSpecified(nullable),
                            upperBound.markNullableAsSpecified(nullable),
                            capabilities,
                            nullable,
                            delegateToUpperBound)
}

fun SimpleType.markNullableAsSpecified(nullable: Boolean): SimpleType {
    if (this is LazyType1) {
        val klass = javaClass
        val delegate: SimpleType
        if (klass == NotNullLazySimpleType::class.java || klass == NullableLazySimpleType::class.java) {
            val delegateLazyType = this as LazyDelegateSimpleType
            if (delegateLazyType.isMarkedNullable == nullable) {
                return this
            }

            delegate = delegateLazyType.delegate
        } else {
            delegate = this
        }

        return if (nullable) {
            NullableLazySimpleType(delegate)
        }
        else {
            NotNullLazySimpleType(delegate)
        }
    }
    else {
        if (nullable == nullable) return this

        return SimpleTypeImpl.create(this, isMarkedNullable = nullable)
    }
}

fun SimpleType.replaceAnnotations(annotations: Annotations): SimpleType {
    if (annotations === this.annotations ||
        annotations.isEmpty() && this.annotations.isEmpty()) return this // TODO: magic optimizations

    if (this is LazyType1) {
        return CustomAnnotations(this, annotations)
    }
    else {
        return SimpleTypeImpl.create(this, annotations = annotations)
    }
}


class SimpleTypeImpl(
        private val annotations: Annotations,
        override val constructor: TypeConstructor,
        override val arguments: List<TypeProjection>,
        override val isMarkedNullable: Boolean,
        override val memberScope: MemberScope,
        override val capabilities: TypeCapabilities
) : SimpleType() {
    companion object {
        fun create(
                delegate: SimpleType,
                annotations: Annotations = delegate.annotations,
                constructor: TypeConstructor = delegate.constructor,
                isMarkedNullable: Boolean = delegate.isMarkedNullable,
                arguments: List<TypeProjection> = delegate.arguments,
                memberScope: MemberScope = delegate.memberScope,
                isError: Boolean = delegate.isError,
                capabilities: TypeCapabilities = delegate.capabilities
        ): SimpleType {
            TODO() // todo
        }

        @JvmStatic
        fun create(
                annotations: Annotations,
                constructor: TypeConstructor,
                isMarkedNullable: Boolean,
                arguments: List<TypeProjection>,
                memberScope: MemberScope,
                capabilities: TypeCapabilities = TypeCapabilities.NONE
        ): SimpleType {
            TODO()
        }

        @JvmStatic fun create(annotations: Annotations,
                              descriptor: ClassDescriptor,
                              nullable: Boolean,
                              arguments: List<TypeProjection>): SimpleType {
            TODO()
        }

    }

    override fun getAnnotations(): Annotations = annotations

    override val isError: Boolean get() = false
}


// this class should be used for delegating to LazyType1
abstract class LazyDelegateSimpleType : SimpleType(), LazyType1 {
    abstract val delegate: SimpleType

    override val constructor: TypeConstructor get() = delegate.constructor
    override val arguments: List<TypeProjection> get() = delegate.arguments
    override val isMarkedNullable: Boolean get() = delegate.isMarkedNullable
    override val memberScope: MemberScope get() = delegate.memberScope
    override val isError: Boolean get() = delegate.isError
    override val capabilities: TypeCapabilities get() = delegate.capabilities
    override fun getAnnotations(): Annotations = delegate.annotations
}

private class NullableLazySimpleType(override val delegate: SimpleType) : LazyDelegateSimpleType() {
    override val isMarkedNullable: Boolean get() = true
}

private class NotNullLazySimpleType(override val delegate: SimpleType) : LazyDelegateSimpleType() {
    override val isMarkedNullable: Boolean get() = false
}

private class CustomAnnotations(
        override val delegate: SimpleType,
        private val annotations: Annotations
): LazyDelegateSimpleType() {
    override fun getAnnotations() = annotations
}