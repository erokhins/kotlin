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

package org.jetbrains.kotlin.utils.profiling

import java.util.*

private val NULL = Object()

object CacheCounterProfilerAgent : ProfilingAgent() {
    private val cacheInfoMap = HashMap<kotlin.String, CacheInfo>()

    private fun getCacheInfo(cacheName: String): CacheInfo {
        val cacheInfo = cacheInfoMap[cacheName]
        if (cacheInfo != null) return cacheInfo

        val newCacheInfo = CacheInfo(cacheName)
        cacheInfoMap[cacheName] = newCacheInfo
        return newCacheInfo
    }

    data class CacheInfo(val name: String, var create: Int = 0, var inCache: Int = 0, var outCache: Int = 0)

    fun inCache(cacheName: String?) {
        if (cacheName == null) return
        getCacheInfo(cacheName).inCache++
    }

    fun createCache(cacheName: String?) {
        if (cacheName == null) return
        getCacheInfo(cacheName).create++
    }

    fun outCache(cacheName: String?) {
        if (cacheName == null) return
        getCacheInfo(cacheName).outCache++
    }

    override fun beforeExit() {
        println("CACHES:")
        cacheInfoMap.values().forEach {
            println(it.toString())
        }
        println()
    }
}

private class JetCache<K, V>(val enabled: Boolean = true, val enableCounter: Boolean = true) {
    val cache = HashMap<K, Any>()

    val name = if (enableCounter) getParentStackTrace(4).getClassName() else null

    init {
        CacheCounterProfilerAgent.createCache(name)
    }

    public fun getOrCompute(key: K, f: (K) -> V): V {
        if (!enabled) return f(key)

        val result = cache[key]
        if (result != null) {
            CacheCounterProfilerAgent.inCache(name)
            if (result == NULL) return null else return result as V
        }
        CacheCounterProfilerAgent.outCache(name)
        val computed = f(key)
        cache[key] = computed ?: NULL
        return computed
    }

}

fun <K, V> createJetCache(enabled: Boolean = true, enableCounter: Boolean = true, compute: (K) -> V): (K) -> V {
    val cache = JetCache<K, V>(enabled, enableCounter)

    return { k : K -> cache.getOrCompute(k, compute) }
}