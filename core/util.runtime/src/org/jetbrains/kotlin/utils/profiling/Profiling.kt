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

import com.yourkit.api.Controller
import java.util.ArrayList
import java.util.HashMap

data class ProfilingSettings(val preloadClasses: String? = null, val yourKitPort: Int? = null)

val profilingAgents = listOf(
        "org.jetbrains.kotlin.utils.profiling.RunTimeAgent",
        "org.jetbrains.kotlin.utils.profiling.PreloadClassesAgent",
        "org.jetbrains.kotlin.utils.profiling.YourKitProfilerAgent",
        "org.jetbrains.kotlin.utils.profiling.CounterProfilerAgent",
        "org.jetbrains.kotlin.utils.profiling.CacheCounterProfilerAgent"
)

val time: String get()= System.currentTimeMillis().toString().reverse().substring(0..3).reverse()

public abstract class ProfilingAgent {
    protected open fun beforeStart() {}
    protected open fun beforeExit() {}

    init {
        agents.add(this)
    }

    public companion object Runner {
        public val profilingSettings: ProfilingSettings get() = _ProfilingSettings ?: ProfilingSettings()

        private val agents = ArrayList<ProfilingAgent>()
        private var _ProfilingSettings: ProfilingSettings? = null

        public fun beforeStart(preloadClasses: String?, enableYourKit: Boolean) {
            _ProfilingSettings = ProfilingSettings(preloadClasses, getYourKitPort(enableYourKit))
            loadProfilingAgents()

            agents.forEach { it.beforeStart() }
        }

        public fun beforeExit() {
            agents.reverse().forEach { it.beforeExit() }
        }

        public fun runPreloadClasses() {
            val s = profilingSettings.preloadClasses
            if (s != null) PreloadClassesAgent.doPreload(s)
        }

        private fun getYourKitPort(enabledYourKit: Boolean): Int? {
            if (!enabledYourKit) return null
            try {
                val controller = Controller()
                return controller.getPort()
            } catch(e: Throwable) {
                return null
            }
        }

        private fun loadProfilingAgents() {
            val classLoader = javaClass.getClassLoader()
            profilingAgents.forEach {
                val clazz = classLoader.loadClass(it)
                clazz.getField("INSTANCE$").get(null)
            }
            assert(profilingAgents.size() == agents.size()) {
                "Not all profiling agents loaded."
            }

            val loadedAgents = agents.map { it.javaClass.getSimpleName() }.joinToString()
            println("Loaded agents: $loadedAgents")
            println()
        }
    }
}

