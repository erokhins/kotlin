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

import org.jetbrains.kotlin.types.typeUtil.isTypeParameter

interface TypeCapability

interface TypeCapabilities {
    object NONE : TypeCapabilities {
        override fun <T : TypeCapability> getCapability(capabilityClass: Class<T>): T? = null
    }

    fun <T : TypeCapability> getCapability(capabilityClass: Class<T>): T?
}

class CompositeTypeCapabilities(private val first: TypeCapabilities, private val second: TypeCapabilities) : TypeCapabilities {
    override fun <T : TypeCapability> getCapability(capabilityClass: Class<T>): T? =
            first.getCapability(capabilityClass) ?: second.getCapability(capabilityClass)
}

class SingletonTypeCapabilities(private val clazz: Class<*>, private val typeCapability: TypeCapability) : TypeCapabilities {
    override fun <T : TypeCapability> getCapability(capabilityClass: Class<T>): T? {
        @Suppress("UNCHECKED_CAST")
        if (capabilityClass == clazz) return typeCapability as T
        return null
    }
}

fun <T : TypeCapability> TypeCapabilities.addCapability(clazz: Class<T>, typeCapability: T): TypeCapabilities {
    if (getCapability(clazz) === typeCapability) return this
    val newCapabilities = SingletonTypeCapabilities(clazz, typeCapability)
    if (this === TypeCapabilities.NONE) return newCapabilities

    return CompositeTypeCapabilities(this, newCapabilities)
}

inline fun <reified T : TypeCapability> KotlinType.getCapability(): T? = getCapability(T::class.java)

interface CustomTypeVariable : TypeCapability {
    fun substitutionResult(replacement: KotlinType): KotlinType
}

fun KotlinType.getCustomTypeVariable(): CustomTypeVariable? {
    if (!isTypeParameter()) return null
    getCapability<CustomTypeVariable>()?.let { return it }

    if (isFlexible()) {
        val flexibility = flexibility()
        return object : CustomTypeVariable {
            override fun substitutionResult(replacement: KotlinType): KotlinType {
                return if (replacement.isFlexible()) {
                    replacement
                }
                else {
                    flexibility.factory.create(replacement, TypeUtils.makeNullable(replacement))
                }
            }
        }
    }
    return null
}

interface SubtypingRepresentatives : TypeCapability {
    val subTypeRepresentative: KotlinType
    val superTypeRepresentative: KotlinType

    fun sameTypeConstructor(type: KotlinType): Boolean
}

fun KotlinType.getSubtypeRepresentative(): KotlinType =
        this.getCapability(SubtypingRepresentatives::class.java)?.subTypeRepresentative ?: this

fun KotlinType.getSupertypeRepresentative(): KotlinType =
        this.getCapability(SubtypingRepresentatives::class.java)?.superTypeRepresentative ?: this

fun sameTypeConstructors(first: KotlinType, second: KotlinType): Boolean {
    val typeRangeCapability = SubtypingRepresentatives::class.java
    return first.getCapability(typeRangeCapability)?.sameTypeConstructor(second) ?: false
           || second.getCapability(typeRangeCapability)?.sameTypeConstructor(first) ?: false
}
