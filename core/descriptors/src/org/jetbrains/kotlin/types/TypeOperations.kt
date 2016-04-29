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
import org.jetbrains.kotlin.types.typeUtil.isDynamic
import org.jetbrains.kotlin.types.typeUtil.simpleOrFlexibleType


fun <T : KotlinType> KotlinType.transform(simple: SimpleType.() -> T,
                                          flexible: KotlinType.FlexibleType.() -> T): T {
    val simpleOrFlexible = simpleOrFlexibleType
    if (simpleOrFlexible is SimpleType) {
        return simple(simpleOrFlexible)
    }
    else {
        return flexible(simpleOrFlexible as KotlinType.FlexibleType)
    }
}

fun KotlinType.markNullableAsSpecifiedNotLazy(nullable: Boolean) = transform({ markNullableAsSpecifiedNotLazy(nullable) }) {
    // Nullability has no effect on dynamics
    if (isDynamic) return@transform this

    KotlinTypeFactory.createFlexibleType(lowerBound.markNullableAsSpecifiedNotLazy(nullable),
                            upperBound.markNullableAsSpecifiedNotLazy(nullable),
                            capabilities)
}

fun SimpleType.markNullableAsSpecifiedNotLazy(nullable: Boolean): SimpleType {
    if (nullable == nullable) return this
    return KotlinTypeFactory.createSimpleType(this, nullable = nullable)
}

fun KotlinType.markNullableAsSpecified(nullable: Boolean) {

}

private abstract class DeferedTypeWithKnownNullability()

private class NullableLazySimpleType(val _delegate: KotlinType) : KotlinType.DeferredType() {
    override fun isComputed(): Boolean = true

    override val delegate: KotlinType
        get() = _delegate.markNullableAsSpecifiedNotLazy(true)

    override val isMarkedNullable: Boolean get() = true
}

private class NotNullLazySimpleType(val _delegate: KotlinType) : KotlinType.DeferredType() {
    override val isMarkedNullable: Boolean get() = false
}

private class CustomAnnotations(
        override val delegate: SimpleType,
        private val annotations: Annotations
): SimpleType(), KotlinType.LazyType {
    override fun getAnnotations() = annotations
}