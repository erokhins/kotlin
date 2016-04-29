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
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.types.checker.KotlinTypeChecker

/**
 * @see KotlinTypeChecker#isSubtypeOf(KotlinType, KotlinType)
 * Also for type creating use KotlinTypeFactory.
 */
sealed class KotlinType : Annotated {
    protected open val delegate: KotlinType
        get() = throw IllegalStateException("Should not be called")

    open val constructor: TypeConstructor get() =  delegate.constructor

    open val arguments: List<TypeProjection> get() = delegate.arguments

    open val isMarkedNullable: Boolean get() = delegate.isMarkedNullable

    open val memberScope: MemberScope get() = delegate.memberScope

    open val isError: Boolean get() = delegate.isError

    @Deprecated("Will be deleted soon")
    open val capabilities: TypeCapabilities get() = delegate.capabilities

    override fun getAnnotations(): Annotations = delegate.annotations

    // ------- internal staff ------

    final override fun hashCode(): Int {
        if (isError) return super.hashCode()

        var result = constructor.hashCode()
        result = 31 * result + arguments.hashCode()
        result = 31 * result + if (isMarkedNullable) 1 else 0
        return result
    }

    final override fun equals(other: Any?): Boolean {
        if (isError) return super.equals(other)

        if (this === other) return true
        if (other !is KotlinType) return false
        if (other.isError) return false

        return isMarkedNullable == other.isMarkedNullable && KotlinTypeChecker.FLEXIBLE_UNEQUAL_TO_INFLEXIBLE.equalTypes(this, other)
    }

    // marker for debug purpose
    internal interface LazyType

    sealed class StableType<T : KotlinType>: KotlinType() {
        abstract fun replaceAnnotations(newAnnotations: Annotations): T
        abstract fun markNullableAsSpecified(newNullability: Boolean): T

        abstract class SimpleType : StableType<SimpleType>() {
            override fun toString(): String {
                // for error types this method should be overridden
                if (isError) return "ErrorType"

                return buildString {
                    for (annotation in annotations.getAllAnnotations()) {
                        append("[", DescriptorRenderer.DEBUG_TEXT.renderAnnotation(annotation.annotation, annotation.target), "] ")
                    }

                    append(constructor)
                    if (!arguments.isEmpty()) arguments.joinTo(this, separator = ", ", prefix = "<", postfix = ">")
                    if (isMarkedNullable) append("?")
                }
            }
        }

        // todo: when we load java descriptors, types is not lazy, because FlexibleType creation use isSubtypeOf
        // it isn't true -- because when we loading type, we know flexibility
        abstract class FlexibleType(val lowerBound: SimpleType, val upperBound: SimpleType) :
                StableType<KotlinType>(), SubtypingRepresentatives {
            companion object {
                @JvmField
                var RUN_SLOW_ASSERTIONS = false
            }

            // These assertions are needed for checking invariants of flexible types.
            //
            // Unfortunately isSubtypeOf is running resolve for lazy types.
            // Because of this we can't run these assertions when we are creating this type. See EA-74904
            //
            // Also isSubtypeOf is not a very fast operation, so we are running assertions only if ASSERTIONS_ENABLED. See KT-7540
            private var assertionsDone = false

            protected fun runAssertions() {
                if (RUN_SLOW_ASSERTIONS || assertionsDone) return
                assertionsDone = true

                assert (lowerBound != upperBound) { "Lower and upper bounds are equal: $lowerBound == $upperBound" }
                assert (KotlinTypeChecker.DEFAULT.isSubtypeOf(lowerBound, upperBound)) {
                    "Lower bound $lowerBound of a flexible type must be a subtype of the upper bound $upperBound"
                }
            }

            override val delegate: SimpleType
                get() {
                    runAssertions()
                    return lowerBound
                }

            override val isError: Boolean get() = false

            override val subTypeRepresentative: KotlinType
                get() = lowerBound
            override val superTypeRepresentative: KotlinType
                get() = upperBound

            override fun sameTypeConstructor(type: KotlinType) = false

            override fun toString(): String = "('$lowerBound'..'$upperBound')"
        }
    }

    /**
     * Only subclasses for this type should be used as wrappers for types.
     *
     * Also you can override some methods from KotlinType, but delegate should have same values.
     * See examples in TypeOperations.kt
     */
    abstract class DeferredType() : KotlinType(), LazyType {
        open fun isComputing(): Boolean = false

        abstract fun isComputed(): Boolean

        public abstract override val delegate: KotlinType

        override fun toString(): String {
            if (isComputed()) {
                return delegate.toString()
            }
            else {
                return "<Not computed yet>"
            }
        }
    }
}
