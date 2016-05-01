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

package org.jetbrains.kotlin.load.java.lazy.types

import org.jetbrains.kotlin.serialization.ProtoBuf
import org.jetbrains.kotlin.serialization.deserialization.FlexibleTypeDeserializer
import org.jetbrains.kotlin.serialization.jvm.JvmProtoBuf
import org.jetbrains.kotlin.types.FlexibleTypeIml
import org.jetbrains.kotlin.types.KotlinType.StableType.FlexibleType
import org.jetbrains.kotlin.types.KotlinType.StableType.SimpleType
import org.jetbrains.kotlin.types.TypeSubstitution

object JvmFlexibleTypeDeserializer : FlexibleTypeDeserializer {
    override val id: String get() = "kotlin.jvm.PlatformType"

    override fun create(proto: ProtoBuf.Type, lowerBound: SimpleType, upperBound: SimpleType): FlexibleType {
        if (proto.hasExtension(JvmProtoBuf.isRaw)) return RawTypeImpl(lowerBound, upperBound)

        return FlexibleTypeIml(lowerBound, upperBound)
    }

    override fun customSubstitutionForBound(proto: ProtoBuf.Type, isLower: Boolean): TypeSubstitution?
            = if (proto.hasExtension(JvmProtoBuf.isRaw)) RawSubstitution else null
}