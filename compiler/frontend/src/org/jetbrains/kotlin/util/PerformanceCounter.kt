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

package org.jetbrains.kotlin.util

import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import java.lang.management.ManagementFactory
import java.util.HashSet
import java.util.concurrent.TimeUnit

public abstract class PerformanceCounter protected constructor(val name: String) {
    companion object {
        private val threadMxBean = ManagementFactory.getThreadMXBean()
        private val allCounters = arrayListOf<PerformanceCounter>()

        private val enteredCounters = ThreadLocal<MutableSet<PerformanceCounter>>()

        private var enabled = true

        init {
            threadMxBean.setThreadCpuTimeEnabled(true)
        }

        private fun enterCounter(counter: PerformanceCounter): Boolean {
            var enteredCountersInThread = enteredCounters.get()
            if (enteredCountersInThread == null) {
                enteredCountersInThread = hashSetOf(counter)
                enteredCounters.set(enteredCountersInThread)
                return true
            }
            return enteredCountersInThread.add(counter)
        }

        private fun leaveCounter(counter: PerformanceCounter) {
            enteredCounters.get()?.remove(counter)
        }

        public fun currentThreadCpuTime(): Long = threadMxBean.getCurrentThreadUserTime()

        public fun report(consumer: (String) -> Unit) {
            allCounters.forEach { it.report(consumer) }
        }

        public fun enabled(configuration: CompilerConfiguration) {
        }
    }

    protected var count: Int = 0
    protected var totalTimeNanos: Long = 0

    public abstract fun increment()

    public abstract fun time<T>(block: () -> T): T

    public fun report(consumer: (String) -> Unit) {
        if (totalTimeNanos == 0L) {
            consumer("$name performed $count times")
        }
        else {
            val millis = TimeUnit.NANOSECONDS.toMillis(totalTimeNanos)
            consumer("$name performed $count times, total time $millis ms")
        }
    }
}

public class SimplePerformanceCounter jvmOverloads constructor(val name: String, val reenterable: Boolean = false) {
    constructor(name: String, excluded: Set<PerformanceCounter>) : this(name, true) {
        excluded.forEach { excludedFrom.add(this) }
    }



    private val excludedFrom: MutableSet<PerformanceCounter> = HashSet()

    private var count: Int = 0
    private var totalTimeNanos: Long = 0
    private var prevStartTime: Long = 0

    private fun start()

    init {
        allCounters.add(this)
    }

    public fun increment() {
        count++
    }

    public fun time<T>(block: () -> T): T {
        count++
        val needTime = !reenterable || enterCounter(this)
        val startTime = currentThreadCpuTime()
        try {
            return block()
        }
        finally {
            if (needTime) {
                totalTimeNanos += currentThreadCpuTime() - startTime
                if (reenterable) leaveCounter(this)
            }
        }
    }

}

