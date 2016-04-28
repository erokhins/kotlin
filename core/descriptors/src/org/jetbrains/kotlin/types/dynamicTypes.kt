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

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.types.checker.KotlinTypeChecker
import org.jetbrains.kotlin.types.typeUtil.builtIns

open class DynamicTypesSettings {
    open val dynamicTypesAllowed: Boolean
        get() = false
}

class DynamicTypesAllowed: DynamicTypesSettings() {
    override val dynamicTypesAllowed: Boolean
        get() = true
}

fun KotlinType.isDynamic(): Boolean = this.capabilities.getCapability(DynamicTypeCapability::class.java) != null

fun createDynamicType(builtIns: KotlinBuiltIns)
        = KotlinType.FlexibleType(builtIns.nothingType, builtIns.anyType,
                                  SingletonTypeCapabilities(DynamicTypeCapability::class.java, DynamicTypeCapability),
                                  isMarkedNullable = false, delegateToUpperBound = true)

object DynamicTypeCapability : TypeCapability {
    val id = "kotlin.DynamicType"
}

object DynamicTypeFactory : FlexibleTypeFactory {
    override val id: String get() = "kotlin.DynamicType"

    override fun create(lowerBound: KotlinType.SimpleType, upperBound: KotlinType.SimpleType): KotlinType.FlexibleType {
        if (KotlinTypeChecker.FLEXIBLE_UNEQUAL_TO_INFLEXIBLE.equalTypes(lowerBound, lowerBound.builtIns.nothingType) &&
            KotlinTypeChecker.FLEXIBLE_UNEQUAL_TO_INFLEXIBLE.equalTypes(upperBound, upperBound.builtIns.nullableAnyType)) {
            return createDynamicType(lowerBound.builtIns)
        }
        else {
            throw IllegalStateException("Illegal type range for dynamic type: $lowerBound..$upperBound")
        }
    }

//        override fun getSpecificityRelationTo(otherType: KotlinType): SpecificityRelation {
//            return if (!otherType.isDynamic()) SpecificityRelation.LESS_SPECIFIC else SpecificityRelation.DONT_KNOW
//        }
}
