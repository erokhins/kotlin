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

package org.jetbrains.kotlin.types.checker

import org.jetbrains.kotlin.types.*
import java.util.*

open class TypeCheckerSettings(val errorTypeEqualsToAnything: Boolean) {
    protected var argumentsDepth = 0
    protected var supertypesStack: Stack<SimpleType>? = null
    protected var stackUsed = false

    open fun isSubtypeByExternalRule(subType: SimpleType, superType: SimpleType) = false

    inline fun <T> runWithArgumentsSettings(subArgument: UnwrappedType, f: TypeCheckerSettings.() -> T): T {
        if (argumentsDepth > 20) {
            error("Recursion detected. Some type: $subArgument")
        }

        argumentsDepth++
        val result = f()
        argumentsDepth--
        return result
    }

    internal fun anySupertype(
            start: SimpleType,
            compute: (SimpleType) -> Boolean,
            supertypesPolicy: (SimpleType) -> SupertypesPolicy
    ): Boolean {
        assert(!stackUsed)

        stackUsed = true

        if (supertypesStack == null) {
            supertypesStack = Stack()
        }
        val stack = supertypesStack!!
        var countSupertypes = 0

        stack.push(start)
        main@ while (stack.isNotEmpty()) {
            if (countSupertypes > 1000) {
                error("Recursive supertypes detected. startType: $start")
            }

            countSupertypes++
            val current = stack.pop()
            if (compute(current)) {
                stackUsed = false
                return true
            }

            val policy = supertypesPolicy(current)
            when (policy) {
                SupertypesPolicy.NONE -> continue@main
                else -> {
                    for (supertype in current.constructor.supertypes) stack.push(policy.transformType(supertype))
                }
            }
        }

        stackUsed = false
        return false
    }

    internal sealed class SupertypesPolicy {
         abstract fun transformType(type: KotlinType): SimpleType

        object NONE: SupertypesPolicy() {
            override fun transformType(type: KotlinType) = throw UnsupportedOperationException("Should not be called")
        }

        object UPPER_IF_FLEXIBLE: SupertypesPolicy() {
            override fun transformType(type: KotlinType) = type.upperIfFlexible()
        }

        object LOWER_IF_FLEXIBLE: SupertypesPolicy() {
            override fun transformType(type: KotlinType) = type.lowerIfFlexible()
        }

        class LowerIfFlexibleWithCustomSubstitutor(val substitutor: TypeSubstitutor): SupertypesPolicy() {
            override fun transformType(type: KotlinType) =
                    substitutor.safeSubstitute(type.lowerIfFlexible(), Variance.INVARIANT).asSimpleType()
        }
    }
}