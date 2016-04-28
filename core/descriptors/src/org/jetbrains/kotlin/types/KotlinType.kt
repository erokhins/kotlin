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

import org.jetbrains.kotlin.descriptors.annotations.Annotated
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.storage.NotNullLazyValue
import org.jetbrains.kotlin.types.checker.KotlinTypeChecker

interface LazyType1

/**
 * @see KotlinTypeChecker#isSubtypeOf(KotlinType, KotlinType)
 */
sealed class KotlinType : Annotated {
    abstract val constructor: TypeConstructor

    abstract val arguments: List<TypeProjection>

    abstract val isMarkedNullable: Boolean

    abstract val memberScope: MemberScope

    abstract val isError: Boolean

    abstract val capabilities: TypeCapabilities

// ------- internal staff ------

    abstract override fun hashCode(): Int
    abstract override fun equals(other: Any?): Boolean
    abstract override fun toString(): String

    // TODO
    abstract class SimpleType : KotlinType() {

        final override fun hashCode(): Int {
            throw UnsupportedOperationException()
        }

        final override fun equals(other: Any?): Boolean {
            throw UnsupportedOperationException()
        }

        override fun toString(): String {
            throw UnsupportedOperationException()
        }
    }

    final class FlexibleType @JvmOverloads constructor(
            val lowerBound: SimpleType,
            val upperBound: SimpleType,
            override val capabilities: TypeCapabilities,
            override val isMarkedNullable: Boolean = lowerBound.isMarkedNullable,
            val delegateToUpperBound: Boolean = false
    ) : KotlinType() {
        // These assertions are needed for checking invariants of flexible types.
        //
        // Unfortunately isSubtypeOf is running resolve for lazy types.
        // Because of this we can't run these assertions when we are creating this type. See EA-74904
        //
        // Also isSubtypeOf is not a very fast operation, so we are running assertions only if ASSERTIONS_ENABLED. See KT-7540
        private var assertionsDone = false

        private fun runAssertions() {
            if (RUN_SLOW_ASSERTIONS || assertionsDone) return
            assertionsDone = true

            assert (lowerBound != upperBound) { "Lower and upper bounds are equal: $lowerBound == $upperBound" }
            assert (KotlinTypeChecker.DEFAULT.isSubtypeOf(lowerBound, upperBound)) {
                "Lower bound $lowerBound of a flexible type must be a subtype of the upper bound $upperBound"
            }
        }

        private val delegate: SimpleType
            get() {
                runAssertions()
                return if (delegateToUpperBound) upperBound else lowerBound
            }

        override val constructor: TypeConstructor get() = delegate.constructor
        override val arguments: List<TypeProjection> get() = delegate.arguments
        override val memberScope: MemberScope get() = delegate.memberScope

        override val isError: Boolean
            get() = false

        // TODO review
        override fun equals(other: Any?): Boolean = delegate.equals(other)
        override fun hashCode(): Int = delegate.hashCode()
        override fun toString(): String = "('$lowerBound'..'$upperBound')"

        override fun getAnnotations(): Annotations = TODO() // TODO

    }

    final class DeferredType(private val lazyDelegate: NotNullLazyValue<KotlinType>) : KotlinType(), LazyType1 {
        fun isComputed() = lazyDelegate.isComputed()
        fun isComputing() = lazyDelegate.isComputing()

        // this type is SimpleType or FlexibleType
        val delegate: KotlinType
            get() {
                var result = lazyDelegate()
                while (result.javaClass == DeferredType::class.java) {
                    result = (result as DeferredType).lazyDelegate()
                }
                return result
            }

        override val constructor: TypeConstructor get() = delegate.constructor
        override val arguments: List<TypeProjection> get() = delegate.arguments
        override val isMarkedNullable: Boolean get() = delegate.isMarkedNullable
        override val memberScope: MemberScope get() = delegate.memberScope
        override val isError: Boolean get() = delegate.isError
        override val capabilities: TypeCapabilities get() = delegate.capabilities
        override fun getAnnotations(): Annotations = delegate.annotations
        override fun hashCode(): Int = delegate.hashCode()
        override fun equals(other: Any?): Boolean = delegate.equals(other)

        override fun toString(): String {
            if (lazyDelegate.isComputed()) {
                return delegate.toString()
            }
            else {
                return "<Not computed yet>"
            }
        }
    }

    companion object {
        @JvmField
        var RUN_SLOW_ASSERTIONS = false
    }
}

val KotlinType.simpleOrFlexibleType: KotlinType
    get() = (this as? KotlinType.DeferredType)?.delegate ?: this


fun KotlinType.transform(simple: KotlinType.SimpleType.() -> KotlinType.SimpleType,
                         flexible: KotlinType.FlexibleType.() -> KotlinType.FlexibleType): KotlinType {
    val simpleOrFlexible = simpleOrFlexibleType
    if (simpleOrFlexible is KotlinType.SimpleType) {
        return simple(simpleOrFlexible)
    }
    else {
        return flexible(simpleOrFlexible as KotlinType.FlexibleType)
    }
}