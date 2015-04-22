/*
 * Copyright 2010-2015 JetBrains s.r.o.
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

package org.jetbrains.kotlin.storage

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

private val NOT_VALUE_NULL = Object()

private class MemorizedFunctionCache<K, V>(
        concurrentMap: ConcurrentMap<K, Any> = ConcurrentHashMap(),
        private val compute: (K) -> V
): Function1<K, V> {
    private val concurrentMap: ConcurrentMap<K, Any> = concurrentMap

    override fun invoke(p1: K): V {
        val value = concurrentMap[p1]
        if (value != null) {
            return if (value == NOT_VALUE_NULL) return null else value as V
        }
        val computed = compute(p1)
        concurrentMap[p1] = computed ?: NOT_VALUE_NULL

        return computed
    }
}

public fun <K, V> createConcurrentMemorizedFunction(compute: (K) -> V): Function1<K, V> = MemorizedFunctionCache(compute = compute)