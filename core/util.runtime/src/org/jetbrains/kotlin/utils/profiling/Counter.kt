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

import java.util.ArrayList
import java.util.HashMap

class JetCounter(val name: String, withTimeCounting: Boolean, val messageLimit: Int) {
    constructor(name: String, withTimeCounting: Boolean) : this(name, withTimeCounting, 30)
    constructor(name: String) : this(name, false)

    var defaultLimit = 10

    var amount: Int = 0
        private set

    private var disable: Boolean = false

    val timeCounter = if (withTimeCounting) RunTimeAgent.Task.createTask(name) else null
    val bigCases = ArrayList<Pair<String, Long>>()
    val messages = HashMap<String, Int>()

    var currentMessage: String? = null

    fun touch() = touch(null)

    fun touch(message: String?) {
        if (disable) return

        message?.touchMessage()
        currentMessage = message
        amount++
        timeCounter?.resume()
    }

    fun <T> touch(message: String?, minLimit: Int = defaultLimit, f: () -> T): T {
        touch(message)
        val t = f()
        touchDone(minLimit)
        return t
    }

    private fun String.touchMessage() {
        messages[this] = (messages[this] ?: 0) + 1
    }

    fun touchDone() = touchDone(defaultLimit)

    fun touchDone(minLimit: Int) {
        if (disable) return

        val time = timeCounter?.pause()
        if (time!= null && currentMessage != null && time > minLimit) {
            bigCases.add(Pair(currentMessage!!, time))
        }
    }

    init {
        CounterProfilerAgent.counters.add(this)
    }

    fun disable(): JetCounter {
        CounterProfilerAgent.counters.remove(this)
        disable = true
        return this
    }
}
val tab = "    "

private fun avr(time: Long, amount: Int): String {
    if (amount == 0) return "none"
    return (time / amount).toString()
}
object CounterProfilerAgent : ProfilingAgent() {
    val counters = ArrayList<JetCounter>()

    override fun beforeExit() {
        counters.forEach {
            val counter = it
            val task = counter.timeCounter

            val baseMessage = "${counter.name}: ${counter.amount}"
            if (task == null) {
                println(baseMessage)
            } else {
                println("$baseMessage; Time spend ${task.total()}(avr: ${avr(task.total(), counter.amount)}, max: ${task.maxTime})")
                counter.bigCases.sortBy { - it.second }.forEach {
                    println("$tab${it.first} : ${it.second}")
                }
            }

            val moreMessages = counter.messages.filterValues { it > counter.messageLimit }.toList().sortBy { -it.second }
            moreMessages.forEach {
                println("$tab$tab${it.first} : ${it.second}")
            }
        }
        println()
    }
}