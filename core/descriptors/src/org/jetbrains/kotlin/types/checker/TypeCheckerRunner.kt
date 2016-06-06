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

import org.jetbrains.kotlin.types.KotlinType

object TypeCheckerRunner {
    fun strictlyEqualsTypes(a: KotlinType, b: KotlinType): Boolean {
        val oldResult = KotlinTypeChecker.FLEXIBLE_UNEQUAL_TO_INFLEXIBLE.equalTypes(a, b)
        val newResult = TypeStrictEqualityChecker.strictEqualTypes(a.unwrap(), b.unwrap())
        return compareAndReport2(a, b, oldResult, newResult)
    }

    fun isSubtypeOf(subType: KotlinType, superType: KotlinType, errorTypesEqualsToAnything: Boolean = false): Boolean {
        val oldResult = oldTypeChecker(errorTypesEqualsToAnything).isSubtypeOf(subType, superType)

        val newResult = with(NewKotlinTypeChecker) {
            TypeCheckerSettings(errorTypesEqualsToAnything).isSubtypeOf(subType.unwrap(), superType.unwrap())
        }

        return compareAndReport(subType, superType, oldResult, newResult)
    }

    fun newIsSubtypeOf(subType: KotlinType, superType: KotlinType): Boolean {
        return with(NewKotlinTypeChecker) {
            TypeCheckerSettings(true).isSubtypeOf(subType.unwrap(), superType.unwrap())
        }
    }

    fun equalsTypes(a: KotlinType, b: KotlinType, errorTypesEqualsToAnything: Boolean = false): Boolean {
        val oldResult = oldTypeChecker(errorTypesEqualsToAnything).equalTypes(a, b)

        val newResult = with(NewKotlinTypeChecker) {
            TypeCheckerSettings(errorTypesEqualsToAnything).equalTypes(a.unwrap(), b.unwrap())
        }

        return compareAndReport(a, b, oldResult, newResult)
    }

    private fun oldTypeChecker(errorTypesEqualsToAnything: Boolean) =
            if (errorTypesEqualsToAnything) KotlinTypeChecker.ERROR_TYPES_ARE_EQUAL_TO_ANYTHING_OLD else KotlinTypeChecker.DEFAULT_OLD

    private fun compareAndReport(subType: KotlinType, superType: KotlinType, oldResult: Boolean, newResult: Boolean): Boolean {
        if (oldResult == newResult) return newResult
        error("Different results: old = $oldResult, new = $newResult, for types: $subType against $superType")
    }

    private fun compareAndReport2(subType: KotlinType, superType: KotlinType, oldResult: Boolean, newResult: Boolean): Boolean {
        if (oldResult != newResult) {
            println("Different results: old = $oldResult, new = $newResult, for types: $subType against $superType")
        }
        return newResult
    }

    object Default : KotlinTypeChecker {
        override fun isSubtypeOf(subtype: KotlinType, supertype: KotlinType): Boolean = isSubtypeOf(subtype, supertype, errorTypesEqualsToAnything = true)
        override fun equalTypes(a: KotlinType, b: KotlinType): Boolean = equalsTypes(a, b, errorTypesEqualsToAnything = true)
    }

    object ErrorTypesAreEqualsToAnything : KotlinTypeChecker {
        override fun isSubtypeOf(subtype: KotlinType, supertype: KotlinType): Boolean = isSubtypeOf(subtype, supertype, errorTypesEqualsToAnything = true)
        override fun equalTypes(a: KotlinType, b: KotlinType): Boolean = equalsTypes(a, b, errorTypesEqualsToAnything = true)
    }
}