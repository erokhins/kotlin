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

package org.jetbrains.kotlin.resolve.calls.model

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.psi.Call
import org.jetbrains.kotlin.psi.ValueArgument
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.types.KotlinType
import java.util.*

interface ResolvedCall<out D : CallableDescriptor> {

    /** The call that was resolved to this ResolvedCall  */
    val call: Call

    /** Type arguments are substituted. This descriptor is guaranteed to have NO declared type parameters  */
    val resultingDescriptor: D

    /** If the target was an extension function or property, this is the value for its receiver parameter  */
    val extensionReceiver: ReceiverValue?

    /** If the target was a member of a class, this is the object of that class to call it on  */
    val dispatchReceiver: ReceiverValue?

    /** Determines whether receiver argument or this object is substituted for explicit receiver  */
    val explicitReceiverKind: ExplicitReceiverKind

    /** Values (arguments) for value parameters  */
    val valueArguments: Map<ValueParameterDescriptor, ResolvedValueArgument>

    /** What's substituted for type parameters  */
    val typeArguments: Map<TypeParameterDescriptor, KotlinType>

    /** The result of mapping the value argument to a parameter */
    val argumentToParameterMap: Map<ValueArgument, ArgumentMatch>
}

/** Values (arguments) for value parameters indexed by parameter index  */
val <D: CallableDescriptor> ResolvedCall<D>.valueArgumentsByIndex: List<ResolvedValueArgument>?
    get() {
        val size = resultingDescriptor.valueParameters.size
        val arguments = ArrayList<ResolvedValueArgument?>(size)
        for (i in 0..size - 1) {
            arguments.add(null)
        }

        for ((parameterDescriptor, value) in valueArguments) {
            val oldValue = arguments.set(parameterDescriptor.index, value)
            if (oldValue != null) {
                return null
            }
        }

        if (arguments.any { it == null }) return null

        @Suppress("UNCHECKED_CAST")
        return arguments as List<ResolvedValueArgument>
    }

fun <D: CallableDescriptor> ResolvedCall<D>.asBackendResolvedCall() : ResolvedCall<D> {
    if (this is VariableAsFunctionResolvedCall) {
        @Suppress("CAST_NEVER_SUCCEEDS")
        return BackendVariableAsFunctionResolvedCall(functionCall.asBackendResolvedCall(), variableCall.asBackendResolvedCall()) as ResolvedCall<D>
    }
    return BackendResolvedCall(call, resultingDescriptor, extensionReceiver, dispatchReceiver, explicitReceiverKind, valueArguments, typeArguments, argumentToParameterMap)
}

class BackendVariableAsFunctionResolvedCall(
        override val functionCall: ResolvedCall<FunctionDescriptor>,
        override val variableCall: ResolvedCall<VariableDescriptor>
) : VariableAsFunctionResolvedCall, ResolvedCall<FunctionDescriptor> by functionCall

class BackendResolvedCall<out D : CallableDescriptor>(
        override val call: Call,
        override val resultingDescriptor: D,
        override val extensionReceiver: ReceiverValue?,
        override val dispatchReceiver: ReceiverValue?,
        override val explicitReceiverKind: ExplicitReceiverKind,
        override val valueArguments: Map<ValueParameterDescriptor, ResolvedValueArgument>,
        override val typeArguments: Map<TypeParameterDescriptor, KotlinType>,
        override val argumentToParameterMap: Map<ValueArgument, ArgumentMatch>
) : ResolvedCall<D>