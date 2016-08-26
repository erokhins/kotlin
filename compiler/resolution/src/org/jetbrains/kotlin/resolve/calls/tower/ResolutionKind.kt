/*
 * Copyright 2010-2016 JetBrains s.r.o.
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

import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.scopes.receivers.DetailedReceiver

interface ResolutionContext {
    val implicitScopeTower: ImplicitScopeTower
    val name: Name
    val explicitReceiver: DetailedReceiver?

    fun createCandidateFactory()
}


sealed class ResolutionKind<D : CallableDescriptor> {
    fun <C : Candidate<D>> createTowerProcessor(
            scopeTower: ImplicitScopeTower,
            explicitReceiver: DetailedReceiver?,
            name: Name,
            candidateFactory: CandidateFactory<D, C>,
            createFactoryProviderForInvoke: () -> CandidateFactoryProviderForInvoke<*, *> = { error("not implemented") }
    ): ScopeTowerProcessor<C> {

    }

    protected class Data<out C: Candidate<D>, in D: CallableDescriptor>(
            val scopeTower: ImplicitScopeTower,  val explicitReceiver: DetailedReceiver?, val name: Name,
            val candidateFactory: CandidateFactory<D, C>, val factoryProvider: CandidateFactoryProviderForInvoke<*, *>
    )

    protected abstract fun <C: Candidate<D>> Data<C, D>.create(): ScopeTowerProcessor<C>


    object Function : ResolutionKind<FunctionDescriptor>() {
        override fun <C : Candidate<FunctionDescriptor>> Data<C, FunctionDescriptor>.create(): ScopeTowerProcessor<C> {
            @Suppress("UNCHECKED_CAST")
            val factoryProviderForInvoke = factoryProvider as CandidateFactoryProviderForInvoke<C, *>

            // a.foo() -- simple function call
            val simpleFunction = createSimpleFunctionProcessor(scopeTower, name, candidateFactory, explicitReceiver)

            // a.foo() -- property a.foo + foo.invoke()
            val invokeProcessor = InvokeTowerProcessor(scopeTower, name, factoryProviderForInvoke, explicitReceiver)

            // a.foo() -- property foo is extension function with receiver a -- a.invoke()
            val invokeExtensionProcessor = createProcessorWithReceiverValueOrEmpty(explicitReceiver) {
                InvokeExtensionTowerProcessor(scopeTower, name, factoryProviderForInvoke, it)
            }

            return CompositeScopeTowerProcessor(simpleFunction, invokeProcessor, invokeExtensionProcessor)
        }
    }

    object VariableAndObject : ResolutionKind<VariableDescriptor>() {
        override fun <C : Candidate<VariableDescriptor>> Data<C, VariableDescriptor>.create(): ScopeTowerProcessor<C> = CompositeScopeTowerProcessor(
                createVariableProcessor(classValueReceiver = true),
                createSimpleProcessor(scopeTower, candidateFactory, explicitReceiver, classValueReceiver = true) { getObjects(name, it) }
        )

    }

    object CallableReference : ResolutionKind<CallableDescriptor>() {
        override fun <C : Candidate<CallableDescriptor>> Data<C, CallableDescriptor>.create(): ScopeTowerProcessor<C> = CompositeScopeTowerProcessor(
                createSimpleFunctionProcessor(scopeTower, name, candidateFactory, explicitReceiver, classValueReceiver = false),
                createVariableProcessor(classValueReceiver = false)
        )

    }

    protected fun <C : Candidate<VariableDescriptor>> Data<C, VariableDescriptor>.createVariableProcessor(classValueReceiver: Boolean = true) =
            createSimpleProcessor(scopeTower, candidateFactory, explicitReceiver, classValueReceiver) { getVariables(name, it) }


}