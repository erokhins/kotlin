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

import java.util.HashMap

object RunTimeAgent : ProfilingAgent() {
    val tasks = HashMap<String, Task>()

    fun startTask(taskName: String): Task = Task.startTask(taskName)

    fun doneTask(taskName: String) {
        assert(tasks[taskName] != null) {
            "Task $taskName must be started"
        }
        tasks[taskName].done()
    }

    fun runTask(taskName: String, task: () -> Unit) {
        val task1 = startTask(taskName)
        task()
        task1.done()
    }

    fun <T> runTaskAndReport(taskName: String, task: () -> T): T {
        val task1 = startTask(taskName)
        val t = task()
        task1.done()

        println("$taskName task done: ${task1.total()} ms")
        println()
        return t
    }

    override fun beforeStart() {
        startTask("Total")
    }

    override fun beforeExit() {
        doneTask("Total")
        tasks.values().sortBy { it.range() }.forEach {
            println("${it.name} task time: ${it.total()} ms")
        }
        println()
    }

    class Task private (val name: String) {
        private var running: Boolean = true

        private var startTime = System.currentTimeMillis()
        private var endTime: Long? = null

        private var runTime = 0L
        var maxTime = 0L
        private set

        fun range(): Long = endTime!!

        fun pause(): Long {
            assert(running)
            running = false
            endTime = System.currentTimeMillis()
            val current = endTime!! - startTime
            runTime += current
            maxTime = Math.max(current, maxTime)
            return current
        }

        fun resume() {
            assert(!running)
            running = true
            startTime = System.currentTimeMillis()
        }

        fun done() {
            pause()
        }

        fun total(): Long {
            assert(!running) {
                "Task $name should be done"
            }
            return runTime
        }

        companion object {
            fun startTask(taskName: String): Task {
                val name = if (tasks[taskName] != null) {
                    taskName + "~" + time
                } else taskName
                assert(tasks[name] == null) {
                    "Task $name already started"
                }

                val task = Task(name)
                tasks[task.name] = task
                return task
            }

            fun createTask(taskName: String): Task {
                val task = startTask(taskName)
                task.pause()
                return task
            }
         }
    }
}