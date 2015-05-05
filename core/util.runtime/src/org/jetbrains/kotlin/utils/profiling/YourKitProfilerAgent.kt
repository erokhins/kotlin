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
import com.yourkit.api.ControllerImpl
import kotlin.platform.platformStatic

object YourKitProfilerAgent : ProfilingAgent() {
    private var controller: ControllerImpl? = null

    override fun beforeStart() {
        if (ProfilingAgent.profilingSettings.yourKitPort != null) {
            controller = ControllerImpl("localhost", ProfilingAgent.profilingSettings.yourKitPort!!)
            println("Enable yourKit profiler agent")
        }
    }

    private var profilingInProcess: PerformanceKind? = null

    fun start(kind: PerformanceKind) {
        assert(profilingInProcess == null)
        profilingInProcess = kind

        when (kind) {
            PerformanceKind.SAMPLING -> {
                controller?.startCPUProfiling(4L, null);
            }
            PerformanceKind.TRACING -> {
                controller?.startCPUProfiling(12L, null);
            }
            PerformanceKind.ALLOCATION ->  {
                // For example, the following call will record allocation of each 10th object OR if object size is 100K or more:
                // controller.startAllocationRecording(true, 10, true, 100*1024)
                controller?.startAllocationRecording(null)
            }
        }
    }

    fun stop(kind: PerformanceKind, name: String? = null) {
        assert(profilingInProcess != null)
        profilingInProcess = null

        val myController = controller
        if (myController != null) {
            val snapshotName = name.toString() + time + kind
            when (kind) {
                PerformanceKind.SAMPLING, PerformanceKind.TRACING -> {
                    myController.stopCPUProfiling()
                    RunTimeAgent.runTaskAndReport("Capture performance snapshot $snapshotName") {
                        myController.captureSnapshot(0L, null, snapshotName, null)
                    }
                }
                PerformanceKind.ALLOCATION -> {
                    myController.stopAllocationRecording()
                    RunTimeAgent.runTaskAndReport("Capture allocation snapshot $snapshotName") {
                        myController.captureSnapshot(1L, null, snapshotName, null)
                    }
                }
            }

        }
    }

    override fun beforeExit() {
        assert(profilingInProcess == null)
    }
}

enum class PerformanceKind {
    SAMPLING
    TRACING
    ALLOCATION
}

class PerformanceSnapshot(val name: String? = null, val kind: PerformanceKind = PerformanceKind.SAMPLING) {
    private var state = 0

    fun start() {
        assert(state == 0) {name.toString()}
        state = 1

        YourKitProfilerAgent.start(kind)
    }

    fun stopAndCapture() {
        assert(state == 1) {name.toString()}
        state = 2
        YourKitProfilerAgent.stop(kind, name)
    }

    fun makeSnapshot<T>(doIt: Boolean, f: () -> T): T {
        val t: T
        if (doIt) {
            start()
            t = f()
            stopAndCapture()
            state = 0
        }
        else {
            t = f()
        }
        return t
    }

    fun stopIfWasStarted() {
        if (state == 1) {
            stopAndCapture()
        }
    }

    companion object {
        platformStatic fun makeSnapshot<T>(name: String, kind: PerformanceKind, f: () -> T): T {
            val a = PerformanceSnapshot(name, kind)
            return a.makeSnapshot(true, f)
        }
    }
}