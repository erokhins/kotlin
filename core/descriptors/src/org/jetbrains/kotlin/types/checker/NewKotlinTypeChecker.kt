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
import org.jetbrains.kotlin.types.typeUtil.makeNotNullable

interface TypeCheckerSettings {

    val errorTypeEqualToAnything: Boolean

    fun <T> runWithArgumentsSettings(f: TypeCheckerSettings.() -> T): T = f() // todo
}

class NewKotlinTypeChecker() {

    /**
     * String! != String & A<String!> != A<String>, also A<in Nothing> != A<out Any?>
     * also A<*> != A<out Any?>
     * different error types non-equals even errorTypeEqualToAnything
     */
    fun isStrictEqualTypes(a: UnwrappedType, b: UnwrappedType): Boolean {
        if (a === b) return true

        if (a is SimpleType && b is SimpleType) return isStrictEqualTypes(a, b)
        if (a is FlexibleType && b is FlexibleType) {
            return isStrictEqualTypes(a.lowerBound, b.lowerBound) &&
                   isStrictEqualTypes(a.upperBound, b.upperBound)
        }
        return false
    }

    fun isStrictEqualTypes(a: SimpleType, b: SimpleType): Boolean {
        if (a.isMarkedNullable != b.isMarkedNullable
            || a.constructor != b.constructor
            || a.arguments.size != b.arguments.size
        ) {
            return false
        }
        for (i in a.arguments.indices) {
            val aArg = a.arguments[i]
            val bArg = b.arguments[i]
            if (aArg.isStarProjection != bArg.isStarProjection) return false

            // both non-star
            if (!aArg.isStarProjection) {
                if (aArg.projectionKind != bArg.projectionKind) return false
                if (isStrictEqualTypes(aArg.type.unwrap(), bArg.type.unwrap())) return false
            }
        }
        return true
    }

    fun TypeCheckerSettings.isEqualTypes(a: UnwrappedType, b: UnwrappedType): Boolean {
        if (a == b) return true

        return isSubtypeOf(a, b) && isSubtypeOf(b, a)
    }

    fun TypeCheckerSettings.isSubtypeOf(subType: UnwrappedType, superType: UnwrappedType) =
        isSubtypeOf(subType.lowerIfFlexible(), superType.upperIfFlexible())

    fun TypeCheckerSettings.isSubtypeOf(subType: SimpleType, superType: SimpleType): Boolean {
        if (subType.isError || superType.isError) {
            if (errorTypeEqualToAnything) {
                return isStrictEqualTypes(subType.makeNullableAsSpecified(false), superType.makeNullableAsSpecified(false))
            }
            else {
                return true
            }
        }

        if (subType.isMarkedNullable && !superType.isMarkedNullable) return false

    }

    fun TypeCheckerSettings.replaceProjectionsToCapturedType(type: SimpleType): SimpleType {
        if (type.arguments.isEmpty() || type.arguments.all { it.projectionKind == Variance.INVARIANT }) return type


    }
}