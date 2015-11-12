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

package org.jetbrains.kotlin.resolve.calls.common

import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.resolve.calls.model.ResolvedValueArgument
import org.jetbrains.kotlin.resolve.scopes.receivers.Qualifier
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.types.KotlinType

interface NewResolvedCall<out D: CallableDescriptor> {
    val call: NewCall

    val resultingDescriptor: D

    val receivers: ResolvedReceivers

    val typeArguments: Map<TypeParameterDescriptor, KotlinType>

    val arguments: Map<ValueParameterDescriptor, ResolvedValueArgument> // todo no psi
}

class ResolvedReceivers(
        val dispatchReceiver: ReceiverValue?,
        val extensionReceiver: ReceiverValue?,
        // for static or top-level calls
        val qualifier: Qualifier?)

// all types here can contains type variable from constraint system
interface IncompleteResolvedCall<out D: CallableDescriptor> : NewResolvedCall<D> {

    val currentDescriptor: D

    override val resultingDescriptor: D
        get() = currentDescriptor

    val constraintSystem: NewConstraintSystem

    // todo data flow info, lambda's
    val children: List<IncompleteResolvedCall<*>>
}

