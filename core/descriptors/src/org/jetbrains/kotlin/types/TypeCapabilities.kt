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

import org.jetbrains.kotlin.types.KotlinType.StableType.FlexibleType
import org.jetbrains.kotlin.types.KotlinType.StableType.SimpleType
import org.jetbrains.kotlin.types.typeUtil.isTypeParameter
import org.jetbrains.kotlin.types.typeUtil.stableType

interface TypeCapability

interface TypeCapabilities {
    object NONE : TypeCapabilities {
        override fun <T : TypeCapability> getCapability(capabilityClass: Class<T>): T? = null
    }

    fun <T : TypeCapability> getCapability(capabilityClass: Class<T>): T?
}

inline fun <reified T : TypeCapability> KotlinType.getCapability(): T? = error("Deleted")

interface CustomTypeVariable {
    fun substitutionResult(replacement: KotlinType): KotlinType
}

fun KotlinType.getCustomTypeVariable(): CustomTypeVariable? {
    if (!isTypeParameter()) return null
    (stableType as? CustomTypeVariable)?.let { return it }

    if (stableType is FlexibleType) {
        return object : CustomTypeVariable {
            override fun substitutionResult(replacement: KotlinType): KotlinType {
                val stableType = replacement.stableType
                return when(stableType) {
                    is FlexibleType -> stableType
                    is SimpleType -> KotlinTypeFactory.createFlexibleType(stableType, stableType.markNullableAsSpecified(true))
                }
            }
        }
    }
    return null
}

interface SubtypingRepresentatives {
    val subTypeRepresentative: KotlinType
    val superTypeRepresentative: KotlinType

    fun sameTypeConstructor(type: KotlinType): Boolean
}

fun KotlinType.getSubtypeRepresentative(): KotlinType =
        (stableType as? SubtypingRepresentatives)?.subTypeRepresentative ?: this

fun KotlinType.getSupertypeRepresentative(): KotlinType =
        (stableType as? SubtypingRepresentatives)?.superTypeRepresentative ?: this

fun sameTypeConstructors(first: KotlinType, second: KotlinType): Boolean {
    return (first.stableType as? SubtypingRepresentatives)?.sameTypeConstructor(second) ?: false
           || (second.stableType as? SubtypingRepresentatives)?.sameTypeConstructor(first) ?: false
}

