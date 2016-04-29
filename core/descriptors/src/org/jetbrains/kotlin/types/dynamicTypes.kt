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
import org.jetbrains.kotlin.types.checker.KotlinTypeChecker
import org.jetbrains.kotlin.types.typeUtil.builtIns

// todo move to frontend
open class DynamicTypesSettings {
    open val dynamicTypesAllowed: Boolean
        get() = false
}

class DynamicTypesAllowed: DynamicTypesSettings() {
    override val dynamicTypesAllowed: Boolean
        get() = true
}

object DynamicTypeFactory : FlexibleTypeFactory {
    override val id: String get() = "kotlin.DynamicType"

    override fun create(lowerBound: SimpleType, upperBound: SimpleType): FlexibleType {
        if (KotlinTypeChecker.FLEXIBLE_UNEQUAL_TO_INFLEXIBLE.equalTypes(lowerBound, lowerBound.builtIns.nothingType) &&
            KotlinTypeChecker.FLEXIBLE_UNEQUAL_TO_INFLEXIBLE.equalTypes(upperBound, upperBound.builtIns.nullableAnyType)) {
            return KotlinTypeFactory.createDynamicType(lowerBound.builtIns)
        }
        else {
            throw IllegalStateException("Illegal type range for dynamic type: $lowerBound..$upperBound")
        }
    }
}
