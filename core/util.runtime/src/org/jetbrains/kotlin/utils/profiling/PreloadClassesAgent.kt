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

import java.io.File
import java.lang.reflect.Modifier

object PreloadClassesAgent: ProfilingAgent() {
    private val classNameRegexp = "\\[Loaded\\s([^\\s]*)\\s".toRegex()
    private val excludedClassPrefix = listOf("java", "sun") + profilingAgents

    private fun String.className(): String? {
        val matcher = classNameRegexp.matcher(this)
        if (matcher.find()) return matcher.group(1)

        return null;
    }

    private fun allowedClassName(name: String) = excludedClassPrefix.none { name.startsWith(it) }

    fun doPreload(filename: String) {
        RunTimeAgent.runTaskAndReport("Preload classes") {
            println("Preload classes list: $filename")

            File(filename).forEachLine {
                val className = it.className()
                if (className != null && allowedClassName(className)) doLoadClass(className)
            }

            println("All classes loaded")
        }
    }

    private fun runWithoutFail(f: () -> Unit) {
        try {
            f()
        } catch (e: Throwable) {
            // do nothing
        }
    }

    private fun touchStaticFields(clazz: Class<*>) {
        clazz.getFields().filter {
            Modifier.isStatic(it.getModifiers())
        }.forEach {
            runWithoutFail { it.get(null) }
        }
    }

    private fun nulls(size: Int): Array<Any?> = Array(size, {null})

    private fun touchStaticMethods(clazz: Class<*>) {
        clazz.getMethods().filter {
            Modifier.isStatic(it.getModifiers())
        }.forEach { method ->
            runWithoutFail {
                method.invoke(null, *nulls(method.getParameterTypes().size()))
            }
        }
    }

    private fun touchConstructors(clazz: Class<*>) {
        runWithoutFail { clazz.newInstance() }
        clazz.getConstructors().forEach { constructor ->
            runWithoutFail {
                constructor.newInstance(*nulls(constructor.getParameterTypes().size()))
            }
        }
    }

    fun doLoadClass(className: String) {
        try {
            val classLoader = this.javaClass.getClassLoader()
            val clazz = classLoader.loadClass(className)
            touchConstructors(clazz)
            touchStaticFields(clazz)
//          touchStaticMethods(clazz)
        }
        catch(e: Throwable) {
            // do nothing
        }
    }
}