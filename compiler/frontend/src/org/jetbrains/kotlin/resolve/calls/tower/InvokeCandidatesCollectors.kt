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

import com.intellij.util.SmartList
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind
import org.jetbrains.kotlin.resolve.calls.tasks.createSynthesizedInvokes
import org.jetbrains.kotlin.resolve.scopes.receivers.Receiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.util.OperatorNameConventions
import java.util.*


internal abstract class AbstractInvokeCollectors<Candidate>(
        protected val functionContext: TowerContext<Candidate>,
        private val variableCollector: TowerCandidatesCollector<Candidate>
) : TowerCandidatesCollector<Candidate> {
    // todo optimize it
    private val previousActions = ArrayList<TowerCandidatesCollector<Candidate>.() -> Unit>()
    private val candidateList: MutableList<Collection<Candidate>> = ArrayList()

    private val invokeCollectorsList: MutableList<Collection<VariableInvokeCollector>> = ArrayList()


    private inner class VariableInvokeCollector(
            val variableCandidate: Candidate,
            val invokeCollector: TowerCandidatesCollector<Candidate> = createInvokeCollector(variableCandidate)
    ): TowerCandidatesCollector<Candidate> by invokeCollector {
        override fun getCandidatesGroups(): List<Collection<Candidate>>
                = invokeCollector.getCandidatesGroups().map { candidateGroup ->
            candidateGroup.map { functionContext.transformCandidate(variableCandidate, it) }
        }
    }

    protected abstract fun createInvokeCollector(variableCandidate: Candidate): TowerCandidatesCollector<Candidate>

    private fun findVariablesAndCreateNewInvokeCandidates() {
        for (variableCandidates in variableCollector.getCandidatesGroups()) {
            val successfulVariables = variableCandidates.filter {
                functionContext.getStatus(it).resolveCandidateLevel.isSuccess
            }

            if (successfulVariables.isNotEmpty()) {
                val invokeCollectors = successfulVariables.map { VariableInvokeCollector(it) }
                invokeCollectorsList.add(invokeCollectors)

                for (previousAction in previousActions) {
                    invokeCollectors.forEach(previousAction)
                    candidateList.addAll(invokeCollectors.collectCandidates())
                }
            }
        }
    }

    private fun Collection<VariableInvokeCollector>.collectCandidates(): List<Collection<Candidate>> {
        return when (size) {
            0 -> emptyList()
            1 -> single().getCandidatesGroups()
            // overload on variables see KT-10093 Resolve depends on the order of declaration for variable with implicit invoke
            else -> listOf(this.flatMap { it.getCandidatesGroups().flatMap { it } })
        }
    }

    private fun runNewAction(action: TowerCandidatesCollector<Candidate>.() -> Unit) {
        candidateList.clear()
        previousActions.add(action)

        for(invokeCollectors in invokeCollectorsList) {
            invokeCollectors.forEach(action)
            candidateList.addAll(invokeCollectors.collectCandidates())
        }

        variableCollector.action()
        findVariablesAndCreateNewInvokeCandidates()
    }

    init { runNewAction { /* do nothing */ } }

    override fun pushTowerLevel(level: TowerLevel) = runNewAction { this.pushTowerLevel(level) }

    override fun pushImplicitReceiver(implicitReceiver: ReceiverValue)
            = runNewAction { this.pushImplicitReceiver(implicitReceiver) }

    override fun getCandidatesGroups(): List<Collection<Candidate>> = SmartList(candidateList)
}

// todo KT-9522 Allow invoke convention for synthetic property
internal class InvokeCollector<Candidate>(
        functionContext: TowerContext<Candidate>,
        private val explicitReceiver: Receiver?
) : AbstractInvokeCollectors<Candidate>(
        functionContext,
        createVariableCollector(functionContext.contextForVariable(stripExplicitReceiver = false), explicitReceiver)
) {

    // todo filter by operator
    override fun createInvokeCollector(variableCandidate: Candidate): TowerCandidatesCollector<Candidate> {
        val (variableReceiver, invokeContext) = functionContext.contextForInvoke(variableCandidate, useExplicitReceiver = false)
        return ExplicitReceiverTowerCandidateCollector(invokeContext, variableReceiver, TowerLevel::getFunctions)
    }
}

internal class InvokeExtensionCollector<Candidate>(
        functionContext: TowerContext<Candidate>,
        private val explicitReceiver: ReceiverValue?
) : AbstractInvokeCollectors<Candidate>(
        functionContext,
        createVariableCollector(functionContext.contextForVariable(stripExplicitReceiver = true), explicitReceiver = null)
) {

    override fun createInvokeCollector(variableCandidate: Candidate): TowerCandidatesCollector<Candidate> {
        val (variableReceiver, invokeContext) = functionContext.contextForInvoke(variableCandidate, useExplicitReceiver = true)
        val invokeDescriptor = functionContext.resolveTower.getExtensionInvokeCandidateDescriptor(variableReceiver)
                               ?: return KnownResultCandidatesCollector(emptyList())
        return InvokeExtensionTowerCandidatesCollector(invokeContext, invokeDescriptor, explicitReceiver)
    }
}

private class InvokeExtensionTowerCandidatesCollector<Candidate>(
        context: TowerContext<Candidate>,
        val invokeCandidateDescriptor: TowerCandidate<FunctionDescriptor>,
        val explicitReceiver: ReceiverValue?
) : AbstractTowerCandidatesCollector<Candidate>(context) {
    override var candidates: Collection<Candidate> = resolve(explicitReceiver)

    private fun resolve(extensionReceiver: ReceiverValue?): Collection<Candidate> {
        if (extensionReceiver == null) return emptyList()

        val candidate = context.createCandidate(invokeCandidateDescriptor, ExplicitReceiverKind.BOTH_RECEIVERS, extensionReceiver)
        return listOf(candidate)
    }

    override fun pushTowerLevel(level: TowerLevel) {
        candidates = emptyList()
    }

    // todo optimize
    override fun pushImplicitReceiver(implicitReceiver: ReceiverValue) {
        candidates = resolve(implicitReceiver)
    }
}

// todo debug info
private fun ResolveTower.getExtensionInvokeCandidateDescriptor(
        possibleExtensionFunctionReceiver: ReceiverValue
): TowerCandidate<FunctionDescriptor>? {
    if (!KotlinBuiltIns.isExactExtensionFunctionType(possibleExtensionFunctionReceiver.type)) return null

    val extFunReceiver = possibleExtensionFunctionReceiver

    return ReceiverTowerLevel(this, extFunReceiver).getFunctions(OperatorNameConventions.INVOKE)
            .single().let {
        assert(it.errors.isEmpty())
        val synthesizedInvoke = createSynthesizedInvokes(listOf(it.descriptor)).single() // todo priority synthesized
        TowerCandidateImpl(extFunReceiver, synthesizedInvoke, listOf(), null)
    }
}

// case 1.(foo())() or
internal fun <Candidate> createCallTowerCollectorsForExplicitInvoke(
        contextForInvoke: TowerContext<Candidate>,
        expressionForInvoke: ReceiverValue,
        explicitReceiver: ReceiverValue?
): TowerCandidatesCollector<Candidate> {
    val invokeExtensionCandidate = contextForInvoke.resolveTower.getExtensionInvokeCandidateDescriptor(expressionForInvoke)
    if (invokeExtensionCandidate == null && explicitReceiver != null) {
        // case 1.(foo())(), where foo() isn't extension function
        return KnownResultCandidatesCollector(emptyList())
    }

    val usualInvoke = ExplicitReceiverTowerCandidateCollector(contextForInvoke, expressionForInvoke, TowerLevel::getFunctions) // todo operator

    if (invokeExtensionCandidate == null) {
        return usualInvoke
    }
    else {
        return CompositeTowerCandidatesCollector(
                usualInvoke,
                InvokeExtensionTowerCandidatesCollector(contextForInvoke, invokeExtensionCandidate, explicitReceiver = explicitReceiver)
        )
    }
}