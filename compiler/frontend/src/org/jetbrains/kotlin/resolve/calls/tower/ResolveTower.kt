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

import org.jetbrains.kotlin.descriptors.ReceiverParameterDescriptor
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.resolve.calls.context.ResolutionContext
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowValue
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowValueFactory
import org.jetbrains.kotlin.resolve.scopes.ImportingScope
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.resolve.scopes.receivers.Qualifier
import org.jetbrains.kotlin.resolve.scopes.receivers.QualifierReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.resolve.scopes.utils.getImplicitReceiversHierarchy
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.utils.addToStdlib.check
import java.util.*

public interface ResolveTower {
    /**
     * Adds receivers to the list in order of locality, so that the closest (the most local) receiver goes first
     */
    val implicitReceiversHierarchy: List<ReceiverParameterDescriptor>

    val explicitReceiver: ReceiverValue?

    val qualifier: QualifierReceiver?

    val location: LookupLocation

    val resolutionContext: ResolutionContext<*>

    val smartCastCache: SmartCastCache

    val levels: Sequence<TowerLevel>
}

public class SmartCastCache(private val resolutionContext: ResolutionContext<*>) {
    private val dataFlowInfo = resolutionContext.dataFlowInfo
    private val smartCastInfoCache = HashMap<ReceiverValue, SmartCastInfo>()

    private fun getSmartCastInfo(receiver: ReceiverValue): SmartCastInfo
            = smartCastInfoCache.getOrPut(receiver) {
        val dataFlowValue = DataFlowValueFactory.createDataFlowValue(receiver, resolutionContext)
        SmartCastInfo(dataFlowValue, dataFlowInfo.getPossibleTypes(dataFlowValue))
    }

    public fun getDataFlowValue(receiver: ReceiverValue): DataFlowValue = getSmartCastInfo(receiver).dataFlowValue

    public fun isStableReceiver(receiver: ReceiverValue): Boolean = getSmartCastInfo(receiver).dataFlowValue.isPredictable

    // exclude receiver.type
    public fun getSmartCastPossibleTypes(receiver: ReceiverValue): Set<KotlinType> = getSmartCastInfo(receiver).possibleTypes

    private data class SmartCastInfo(val dataFlowValue: DataFlowValue, val possibleTypes: Set<KotlinType>)
}


internal class ResolveTowerImpl(
        override val resolutionContext: ResolutionContext<*>,
        receiver: ReceiverValue?,
        override val location: LookupLocation
): ResolveTower {
    override val explicitReceiver: ReceiverValue? = receiver?.check { it !is Qualifier }
    override val qualifier: QualifierReceiver? = receiver as? QualifierReceiver

    override val smartCastCache = SmartCastCache(resolutionContext)

    override val implicitReceiversHierarchy = resolutionContext.scope.getImplicitReceiversHierarchy()

    // todo val?
    override val levels: Sequence<TowerLevel> = LevelIterator().asSequence()

    private enum class IteratorState {
        NOT_START,
        RECEIVER,
        SCOPE,
        IMPORTING_SCOPE,
        END
    }

    private inner class LevelIterator(): Iterator<TowerLevel> {
        private var currentLexicalScope: LexicalScope = resolutionContext.scope
        private var state = IteratorState.NOT_START
        private var allKnownReceivers: Collection<KotlinType>? = null

        private fun currentScopeAsScopeLevel(): TowerLevel {
            if (currentLexicalScope is ImportingScope) {

                // before we creating this level all smartCasts for implicit receivers will be calculated
                if (allKnownReceivers == null) {
                    allKnownReceivers = (implicitReceiversHierarchy.map { it.value } fastPlus explicitReceiver).flatMap {
                        smartCastCache.getSmartCastPossibleTypes(it) fastPlus it.type
                    }
                }

                state = IteratorState.IMPORTING_SCOPE.check { currentLexicalScope.parent != null } ?: IteratorState.END

                return ImportingScopeTowerLevel(this@ResolveTowerImpl, currentLexicalScope as ImportingScope, allKnownReceivers!!)
            }
            else {
                state = IteratorState.SCOPE
                return ScopeTowerLevel(this@ResolveTowerImpl, currentLexicalScope)
            }
        }

        private fun entranceToScope(scope: LexicalScope): TowerLevel {
            currentLexicalScope = scope
            val implicitReceiver = currentLexicalScope.implicitReceiver
            if (implicitReceiver != null) {
                state = IteratorState.RECEIVER
                return ReceiverTowerLevel(this@ResolveTowerImpl, implicitReceiver.value)
            }
            else {
                return currentScopeAsScopeLevel()
            }
        }

        override fun next(): TowerLevel {
            return when (state) {
                IteratorState.NOT_START -> {
                    entranceToScope(currentLexicalScope)
                }
                IteratorState.RECEIVER -> {
                    currentScopeAsScopeLevel()
                }
                IteratorState.SCOPE, IteratorState.IMPORTING_SCOPE -> {
                    val parent = currentLexicalScope.parent
                    assert(parent != null) {
                        "This should never happened because of currentScopeAsScopeLevel(), currentScope: $currentLexicalScope"
                    }
                    entranceToScope(parent!!)

                }
                else -> throw IllegalStateException("Illegal state: $state, currentScope: $currentLexicalScope")
            }
        }

        override fun hasNext() = state != IteratorState.END

    }

}