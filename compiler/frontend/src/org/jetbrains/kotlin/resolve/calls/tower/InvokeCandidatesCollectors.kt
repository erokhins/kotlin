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

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.ReceiverParameterDescriptor
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.resolve.TemporaryBindingTrace
import org.jetbrains.kotlin.resolve.calls.CallTransformer
import org.jetbrains.kotlin.resolve.calls.context.ContextDependency
import org.jetbrains.kotlin.resolve.calls.model.MutableResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.VariableAsFunctionResolvedCallImpl
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind
import org.jetbrains.kotlin.resolve.calls.tasks.TracingStrategyForInvoke
import org.jetbrains.kotlin.resolve.calls.tasks.createSynthesizedInvokes
import org.jetbrains.kotlin.resolve.calls.tower.OverloadTowerResolver.OverloadTowerResolverContext
import org.jetbrains.kotlin.resolve.scopes.receivers.ExpressionReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.types.typeUtil.makeNotNullable
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.kotlin.utils.addToStdlib.check
import java.util.*


internal abstract class AbstractInvokeCollectors(
        context: OverloadTowerResolverContext,
        variableCollectors: List<TowerCandidatesCollector<VariableDescriptor>>
) : AbstractTowerCandidatesCollector<FunctionDescriptor>(context) {
    protected val previousLevels = ArrayList<TowerLevel>()

    private val variableResolver = SuccessfulCandidatesCollectionWrapper(variableCollectors)
    private var variableResolvedCall: MutableResolvedCall<VariableDescriptor>? = null
    private var variableResolveFailed = false

    private lateinit var invokeCollector: TowerCandidatesCollector<FunctionDescriptor>

    private var currentCandidates: Collection<Pair<VariableAsFunctionResolvedCallImpl, ResolveCandidateStatus>> = emptyList()

    override fun pushTowerLevel(level: TowerLevel) {
        if (variableResolveFailed) return

        previousLevels.add(level)
        if (variableResolvedCall == null) {
            variableResolver.pushTowerLevel(level)
            updateVariableResolve()
        }
        else {
            invokeCollector.pushTowerLevel(level)

            updateInvokeResolve()
        }
    }

    override fun pushImplicitReceiver(implicitReceiver: ReceiverParameterDescriptor) {
        if (variableResolveFailed) return
        if (variableResolvedCall == null) {
            variableResolver.pushImplicitReceiver(implicitReceiver)
            updateVariableResolve()
        }
        else {
            invokeCollector.pushImplicitReceiver(implicitReceiver)
            updateInvokeResolve()
        }
    }

    override fun getCurrentCandidates(): Collection<Pair<MutableResolvedCall<FunctionDescriptor>, ResolveCandidateStatus>> {
        if (variableResolveFailed) return emptyList()

        return currentCandidates
    }

    init {
        updateVariableResolve()
    }

    private fun updateVariableResolve() {
        assert(variableResolvedCall == null)

        val newVariables = variableResolver.getCurrentCandidates()

        // todo overloads (after smart cast) KT-9517, also  KT-9518, KT-9523
        val result = context.overloadTowerResolver.resolutionResultsHandler.chooseAndReportMaximallySpecific<VariableDescriptor>(newVariables.mapTo(HashSet()) {it.first}, true, true)

        if (!result.isSuccess && !result.isIncomplete) {
            if (newVariables.isNotEmpty()) {
                variableResolveFailed = true
                return
            }
        }
        else {
            variableResolvedCall = result.resultingCall
            val variableResolvedCall = variableResolvedCall!!
            // todo hack
            context.tracing.bindReference(variableResolvedCall.trace, variableResolvedCall)
            context.tracing.bindResolvedCall(variableResolvedCall.trace, variableResolvedCall)


            val calleeExpression = context.basicCallContext.call.calleeExpression
            if (calleeExpression == null) {
                variableResolveFailed = true
                return
            }
            val variableReceiver = ExpressionReceiver(calleeExpression, variableResolvedCall.resultingDescriptor.type)

            invokeCollector = createInvokeCollector(variableResolvedCall, variableReceiver)

            val resultCollector = ResultAndTowerCandidatesCollector(invokeCollector)
            previousLevels.forEach { resultCollector.pushTowerLevel(it) }

            currentCandidates = resultCollector.getCurrentCandidates().map {
                VariableAsFunctionResolvedCallImpl(it.first, variableResolvedCall) to it.second
            }
        }
    }

    private fun updateInvokeResolve() {
        currentCandidates = invokeCollector.getCurrentCandidates().map {
            VariableAsFunctionResolvedCallImpl(it.first, variableResolvedCall!!) to it.second
        }
    }

    protected abstract fun createInvokeCollector(
            variableResolvedCall: MutableResolvedCall<VariableDescriptor>,
            variableReceiver: ExpressionReceiver
    ): TowerCandidatesCollector<FunctionDescriptor>



    private class SuccessfulCandidatesCollectionWrapper<D : CallableDescriptor>(
            val delegates: List<TowerCandidatesCollector<D>>
    ) : TowerCandidatesCollector<D> {

        override fun pushTowerLevel(level: TowerLevel) = delegates.forEach { it.pushTowerLevel(level) }

        override fun pushImplicitReceiver(implicitReceiver: ReceiverParameterDescriptor) = delegates.forEach { it.pushImplicitReceiver(implicitReceiver) }

        override fun getCurrentCandidates(): Collection<Pair<MutableResolvedCall<D>, ResolveCandidateStatus>> {
            for (delegate in delegates) { // order of collector is essential here
                val candidates = delegate.getCurrentCandidates().filter { it.second.resolveCandidateLevel == ResolveCandidateLevel.RESOLVED }
                if (candidates.isNotEmpty()) return candidates
            }
            return emptyList()
        }
    }

    private class ResultAndTowerCandidatesCollector<D : CallableDescriptor>(
            val delegate: TowerCandidatesCollector<D>
    ) : ResultCollector<D>(), TowerCandidatesCollector<D> {
        init {
            pushCandidates(delegate.getCurrentCandidates())
        }

        override fun pushTowerLevel(level: TowerLevel) {
            delegate.pushTowerLevel(level)
            pushCandidates(delegate.getCurrentCandidates())
        }

        override fun pushImplicitReceiver(implicitReceiver: ReceiverParameterDescriptor) {
            delegate.pushImplicitReceiver(implicitReceiver)
            pushCandidates(delegate.getCurrentCandidates())
        }

        override fun getCurrentCandidates() = getFinalCandidates()
    }
}

// todo KT-9522 Allow invoke convention for synthetic property
internal class InvokeCollector(context: OverloadTowerResolverContext) :
        AbstractInvokeCollectors(context, createVariableCollectorForInvoke(context)) {

    override fun createInvokeCollector(
            variableResolvedCall: MutableResolvedCall<VariableDescriptor>,
            variableReceiver: ExpressionReceiver
    ): TowerCandidatesCollector<FunctionDescriptor> {
        val functionCall = CallTransformer.CallForImplicitInvoke(ReceiverValue.NO_RECEIVER, variableReceiver, context.basicCallContext.call)
        val tracingForInvoke = TracingStrategyForInvoke(variableReceiver.expression, functionCall, variableReceiver.type)

        val basicCallResolutionContext = context.basicCallContext.replaceBindingTrace(variableResolvedCall.trace).replaceContextDependency(ContextDependency.DEPENDENT) // todo

        val newContext = OverloadTowerResolverContext(context.overloadTowerResolver, basicCallResolutionContext, OperatorNameConventions.INVOKE, tracingForInvoke)

        // todo filter by operator
        return ExplicitReceiverTowerCandidateCollector(newContext, variableReceiver) { getFunctions(it) }
    }
}

internal class InvokeExtensionCollector(context: OverloadTowerResolverContext) :
        AbstractInvokeCollectors(context, createVariableCollectorForInvoke(context, explicitReceiver = null, qualifier = null)) {

    override fun createInvokeCollector(
            variableResolvedCall: MutableResolvedCall<VariableDescriptor>,
            variableReceiver: ExpressionReceiver
    ): TowerCandidatesCollector<FunctionDescriptor> {
        val invokeDescriptor = context.getExtensionInvokeCandidateDescriptor(variableReceiver)
        if (invokeDescriptor != null) {
            // todo hack
            val functionCall = CallTransformer.CallForImplicitInvoke(ReceiverValue.NO_RECEIVER, variableReceiver, context.basicCallContext.call)
            val tracingForInvoke = TracingStrategyForInvoke(variableReceiver.expression, functionCall, variableReceiver.type)
            val basicCallResolutionContext = context.basicCallContext.replaceBindingTrace(variableResolvedCall.trace).replaceContextDependency(ContextDependency.DEPENDENT) // todo
            val newContext = OverloadTowerResolverContext(context.overloadTowerResolver, basicCallResolutionContext, OperatorNameConventions.INVOKE, tracingForInvoke)

            return InvokeExtensionTowerCandidatesCollector(newContext, variableResolvedCall, invokeDescriptor, context.resolveTower.explicitReceiver)
        }

        return KnownResultCandidatesCollector(emptyList())
    }

    private class InvokeExtensionTowerCandidatesCollector(
            context: OverloadTowerResolverContext,
            val variableResolvedCall: MutableResolvedCall<VariableDescriptor>,
            val invokeCandidateDescriptor: CandidateDescriptor<FunctionDescriptor>,
            val explicitReceiver: ReceiverValue?
    ) : AbstractTowerCandidatesCollector<FunctionDescriptor>(context) {
        private var currentCandidates = resolve(explicitReceiver)

        private fun resolve(extensionReceiver: ReceiverValue?)
                : Collection<Pair<MutableResolvedCall<FunctionDescriptor>, ResolveCandidateStatus>> {
            if (extensionReceiver == null || !extensionReceiver.exists()) return emptyList()

            val candidate = checkCandidate(invokeCandidateDescriptor, ExplicitReceiverKind.BOTH_RECEIVERS, extensionReceiver)
            return listOf(VariableAsFunctionResolvedCallImpl(candidate.first, variableResolvedCall) to candidate.second)
        }

        override fun pushTowerLevel(level: TowerLevel) {
            currentCandidates = emptyList()
        }

        override fun pushImplicitReceiver(implicitReceiver: ReceiverParameterDescriptor) {
            currentCandidates = resolve(implicitReceiver.value)
        }

        override fun getCurrentCandidates() = currentCandidates
    }
}

// todo debug info
private fun OverloadTowerResolverContext.getExtensionInvokeCandidateDescriptor(
        possibleExtensionFunctionReceiver: ReceiverValue
): CandidateDescriptor<FunctionDescriptor>? {
    if (!KotlinBuiltIns.isExactExtensionFunctionType(possibleExtensionFunctionReceiver.type)) return null

    val extFunReceiver = possibleExtensionFunctionReceiver

    return ReceiverTowerLevel(resolveTower, extFunReceiver).getFunctions(OperatorNameConventions.INVOKE)
            .single().let {
        assert(it.errors.isEmpty())
        val synthesizedInvoke = createSynthesizedInvokes(listOf(it.descriptor)).single() // todo priority synthesized
        CandidateDescriptorImpl(extFunReceiver, synthesizedInvoke, listOf(), null)
    }
}

internal class ExplicitExtensionInvokeCallTowerCollector(
        context: OverloadTowerResolverContext,
        val call: CallTransformer.CallForImplicitInvoke,
        val invokeExtensionDescriptor: CandidateDescriptor<FunctionDescriptor>
): AbstractTowerCandidatesCollector<FunctionDescriptor>(context) {
    private var currentCandidates = resolve(call.explicitReceiver.check { it.exists() })

    private fun resolve(extensionReceiver: ReceiverValue?)
            : Collection<Pair<MutableResolvedCall<FunctionDescriptor>, ResolveCandidateStatus>> {
        if (extensionReceiver == null || !extensionReceiver.exists()) return emptyList()

        return listOf(checkCandidate(invokeExtensionDescriptor, ExplicitReceiverKind.BOTH_RECEIVERS, extensionReceiver))
    }

    override fun pushTowerLevel(level: TowerLevel) {
        currentCandidates = emptyList()
    }

    override fun pushImplicitReceiver(implicitReceiver: ReceiverParameterDescriptor) {
        currentCandidates = resolve(implicitReceiver.value)
    }

    override fun getCurrentCandidates() = currentCandidates
}

internal fun createCallTowerCollectorsForExplicitInvoke(
        context: OverloadTowerResolverContext
): TowerCandidatesCollector<FunctionDescriptor>? {
    val call = context.basicCallContext.call as? CallTransformer.CallForImplicitInvoke ?: return null

    val invokeExtensionCandidate = context.getExtensionInvokeCandidateDescriptor(call.dispatchReceiver)
    if (invokeExtensionCandidate != null) {
        return ExplicitExtensionInvokeCallTowerCollector(context, call, invokeExtensionCandidate)
    }
    else {
        // case 1.(foo())(), where foo() isn't extension function
        if (call.explicitReceiver.exists()) return KnownResultCandidatesCollector(emptyList())

        return ExplicitReceiverTowerCandidateCollector(context, call.dispatchReceiver) { getFunctions(it) } // todo operator
    }
}