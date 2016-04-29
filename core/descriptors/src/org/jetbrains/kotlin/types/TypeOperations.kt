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

import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.types.KotlinType.SimpleType
import org.jetbrains.kotlin.types.KotlinType.StableType
import org.jetbrains.kotlin.types.typeUtil.unwrappedType


inline fun KotlinType.transform(stable: StableType<*>.() -> KotlinType, simpleUnstable: SimpleType.() -> KotlinType): KotlinType {
    val unwrappedType = unwrappedType
    if (unwrappedType is StableType<*>) {
        return stable(unwrappedType)
    }
    else {
        // FlexibleType is StableType => unwrappedType is SimpleType
        return simpleUnstable(unwrappedType as SimpleType)
    }
}

fun KotlinType.markNullableAsSpecifiedNotLazy(nullable: Boolean): KotlinType = transform({ replaceNullability(nullable) }) {
    if (isMarkedNullable == nullable) {
        this
    }
    else {
        KotlinTypeFactory.createSimpleType(this, nullable = nullable)
    }
}

fun SimpleType.markNullableAsSpecifiedNotLazy(nullable: Boolean)
        = (this as KotlinType).markNullableAsSpecifiedNotLazy(nullable) as SimpleType

fun KotlinType.markNullableAsSpecified(nullable: Boolean): KotlinType {
    if (this is DeferredTypeWithKnownNullability) {
        if (nullable == isMarkedNullable) {
            return this
        }
        else {
            return DeferredTypeWithKnownNullability.create(_delegate, nullable)
        }
    }

    return DeferredTypeWithKnownNullability.create(this, nullable)
}

private sealed class DeferredTypeWithKnownNullability(val _delegate: KotlinType) : KotlinType.DeferredType() {
    override fun isComputed(): Boolean = true

    override val delegate: KotlinType
        get() = _delegate.markNullableAsSpecifiedNotLazy(isMarkedNullable)

    private class Nullable(delegate: KotlinType) : DeferredTypeWithKnownNullability(delegate) {
        override val isMarkedNullable: Boolean get() = true
    }

    private class NotNull(delegate: KotlinType) : DeferredTypeWithKnownNullability(delegate) {
        override val isMarkedNullable: Boolean get() = false
    }

    companion object {
        fun create(delegate: KotlinType, nullable: Boolean) = if (nullable) Nullable(delegate) else NotNull(delegate)
    }
}

fun KotlinType.replaceAnnotationsNotLazy(newAnnotations: Annotations) = transform({ replaceAnnotations(newAnnotations) }) {
    if (annotations === newAnnotations) {
        this
    }
    else {
        KotlinTypeFactory.createSimpleType(this, annotations = newAnnotations)
    }
}

fun SimpleType.replaceAnnotationsNotLazy(newAnnotations: Annotations)
        = (this as KotlinType).replaceAnnotationsNotLazy(newAnnotations) as SimpleType

fun KotlinType.replaceAnnotations(newAnnotations: Annotations): KotlinType = DeferredTypeWithKnownAnnotations(this, newAnnotations)

private class DeferredTypeWithKnownAnnotations(
        val _delegate: KotlinType,
        private val annotations: Annotations
): KotlinType.DeferredType() {
    override fun isComputed(): Boolean = true

    override val delegate: KotlinType
        get() = _delegate.replaceAnnotationsNotLazy(annotations)

    override fun getAnnotations() = annotations
}