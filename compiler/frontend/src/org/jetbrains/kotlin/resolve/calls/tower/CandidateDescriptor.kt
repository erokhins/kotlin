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

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.callResolverUtil.isOrOverridesSynthesized
import org.jetbrains.kotlin.resolve.calls.tower.ResolveCandidateLevel.IMPOSSIBLE_TO_GENERATE
import org.jetbrains.kotlin.resolve.calls.tower.ResolveCandidateLevel.MAY_THROW_RUNTIME_ERROR
import org.jetbrains.kotlin.resolve.calls.tower.ResolveCandidateLevel.OTHER
import org.jetbrains.kotlin.resolve.calls.tower.ResolveCandidateLevel.RUNTIME_ERROR
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.types.ErrorUtils
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.expressions.ExpressionTypingUtils

public interface CandidateDescriptor<out D : CallableDescriptor> {
    val errors: List<ResolveCandidateError>

    val isSynthetic: Boolean // todo dynamics calls
        get() = descriptor is CallableMemberDescriptor && isOrOverridesSynthesized(descriptor as CallableMemberDescriptor)

    val requiredExtensionParameter: Boolean
        get() = descriptor.extensionReceiverParameter != null

    val dispatchReceiver: ReceiverValue?

    val dispatchReceiverSmartCastType: KotlinType? // todo fake override

    val descriptor: D
}

// todo error for this access from nested class
public class VisibilityError(val invisibleMember: DeclarationDescriptorWithVisibility): ResolveCandidateError(RUNTIME_ERROR)
public class UnstableSmartCast(): ResolveCandidateError(MAY_THROW_RUNTIME_ERROR)
public class ErrorDescriptor(): ResolveCandidateError(OTHER)
public class NestedClassViaInstanceReference(val classDescriptor: ClassDescriptor): ResolveCandidateError(IMPOSSIBLE_TO_GENERATE)
public class InnerClassViaStaticReference(val classDescriptor: ClassDescriptor): ResolveCandidateError(IMPOSSIBLE_TO_GENERATE)


public class UnsupportedInnerClassCall(val message: String): ResolveCandidateError(IMPOSSIBLE_TO_GENERATE)

internal class CandidateDescriptorImpl<D : CallableDescriptor>(
        override val dispatchReceiver: ReceiverValue?,
        override val descriptor: D,
        override val errors: List<ResolveCandidateError>,
        override val dispatchReceiverSmartCastType: KotlinType?
) : CandidateDescriptor<D>

internal fun checkVisibility(
        ownerDescriptor: DeclarationDescriptor,
        descriptor: DeclarationDescriptorWithVisibility,
        bindingContext: BindingContext,
        dispatchReceiver: ReceiverValue
): ResolveCandidateError? {
    if (ErrorUtils.isError(descriptor)) return ErrorDescriptor()

    val receiverValue = ExpressionTypingUtils.normalizeReceiverValueForVisibility(dispatchReceiver, bindingContext)
    val invisibleMember = Visibilities.findInvisibleMember(receiverValue, descriptor, ownerDescriptor)
    return invisibleMember?.let { VisibilityError(it) }
}

internal fun <D : CallableDescriptor> CandidateDescriptor<D>.addError(error: ResolveCandidateError?): CandidateDescriptor<D> {
    if (error == null) return this
    return CandidateDescriptorImpl(dispatchReceiver, descriptor, errors + error, dispatchReceiverSmartCastType)
}