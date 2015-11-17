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

package org.jetbrains.kotlin.resolve.calls.tower

import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.utils.addToStdlib.check

interface TowerContext<Candidate> {
    val name: Name
    val resolveTower: ResolveTower

    fun createCandidate(
            towerCandidate: TowerCandidate<*>,
            explicitReceiverKind: ExplicitReceiverKind,
            extensionReceiver: ReceiverValue?
    ): Candidate

    fun getStatus(candidate: Candidate): ResolveCandidateStatus

    fun transformCandidate(variable: Candidate, invoke: Candidate): Candidate

    fun contextForVariable(stripExplicitReceiver: Boolean): TowerContext<Candidate>

    // foo() -> ReceiverValue(foo), context for invoke
    fun contextForInvoke(variable: Candidate, useExplicitReceiver: Boolean): Pair<ReceiverValue, TowerContext<Candidate>>
}

internal interface TowerCandidatesCollector<Candidate> {

    fun pushTowerLevel(level: TowerLevel)
    fun pushImplicitReceiver(implicitReceiver: ReceiverValue)

    // candidates with matched receivers (dispatch receiver was already matched in TowerLevel)
    //
    fun getCandidatesGroups(): List<Collection<Candidate>>
}

internal class TowerResolver {
    fun <Candidate> runResolve(
            context: TowerContext<Candidate>,
            collector: TowerCandidatesCollector<Candidate>,
            useOrder: Boolean = true
    ): Collection<Candidate> {

        val resultCollector = ResultCollector<Candidate> { context.getStatus(it) }

        fun pushSomething(action: TowerCandidatesCollector<Candidate>.() -> Unit): Collection<Candidate>? {
            collector.action()
            val candidatesGroups = if (useOrder) {
                    collector.getCandidatesGroups()
                }
                else {
                    listOf(collector.getCandidatesGroups().flatMap { it })
                }

            for (candidatesGroup in candidatesGroups) {
                resultCollector.pushCandidates(candidatesGroup)
                resultCollector.getResolved()?.let { return it }
            }
            return null
        }

        // possible there is explicit member
        pushSomething {  }?.let { return it }

        for (level in context.resolveTower.levels) {
            pushSomething { pushTowerLevel(level) }?.let { return it }

            for (implicitReceiver in context.resolveTower.implicitReceivers) {
                pushSomething { pushImplicitReceiver(implicitReceiver) }?.let { return it }
            }
        }

        return resultCollector.getFinalCandidates()
    }


    // collect all candidates
    internal class ResultCollector<Candidate>(private val getStatus: (Candidate) -> ResolveCandidateStatus) {
        private var currentCandidates: Collection<Candidate> = emptyList()
        private var currentLevel: ResolveCandidateLevel? = null

        fun getResolved() = currentCandidates.check { currentLevel == ResolveCandidateLevel.RESOLVED }

        fun getSyntheticResolved() = currentCandidates.check { currentLevel == ResolveCandidateLevel.RESOLVED_SYNTHETIC }

        fun getErrors() = currentCandidates.check {
            currentLevel == null || currentLevel!! > ResolveCandidateLevel.RESOLVED_SYNTHETIC
        }

        fun getFinalCandidates() = getResolved() ?: getSyntheticResolved() ?: getErrors() ?: emptyList()

        fun pushCandidates(candidates: Collection<Candidate>) {
            if (candidates.isEmpty()) return
            val minimalLevel = candidates.map { getStatus(it).resolveCandidateLevel }.min()!!
            if (currentLevel == null || currentLevel!! > minimalLevel) {
                currentLevel = minimalLevel
                currentCandidates = candidates.filter { getStatus(it).resolveCandidateLevel == minimalLevel }
            }
        }
    }
}
