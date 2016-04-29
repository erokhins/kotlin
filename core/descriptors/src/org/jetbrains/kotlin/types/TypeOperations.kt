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
import org.jetbrains.kotlin.types.typeUtil.stableType

fun KotlinType.markNullableAsSpecified(nullability: Boolean): KotlinType {
    if (this is DeferredTypeWithKnownNullability) {
        if (nullability == isMarkedNullable) {
            return this
        }
        else {
            return DeferredTypeWithKnownNullability.create(_delegate, nullability)
        }
    }

    return DeferredTypeWithKnownNullability.create(this, nullability)
}

private sealed class DeferredTypeWithKnownNullability(val _delegate: KotlinType) : KotlinType.DeferredType() {
    override fun isComputed(): Boolean = true

    override val delegate: KotlinType
        get() = _delegate.stableType.markNullableAsSpecified(isMarkedNullable)

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

fun KotlinType.replaceAnnotations(newAnnotations: Annotations): KotlinType = DeferredTypeWithKnownAnnotations(this, newAnnotations)

private class DeferredTypeWithKnownAnnotations(
        val _delegate: KotlinType,
        private val annotations: Annotations
): KotlinType.DeferredType() {
    override fun isComputed(): Boolean = true

    override val delegate: KotlinType
        get() = _delegate.stableType.replaceAnnotations(annotations)

    override fun getAnnotations() = annotations
}