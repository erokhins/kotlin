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

package org.jetbrains.kotlin.resolve.calls

import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.resolve.calls.model.SimpleCallArgument
import org.jetbrains.kotlin.types.TypeSubstitutor
import org.jetbrains.kotlin.types.UnwrappedType
import org.jetbrains.kotlin.types.Variance


fun <D : CallableDescriptor> D.safeSubstitute(substitutor: TypeSubstitutor): D =
        @Suppress("UNCHECKED_CAST") (substitute(substitutor) as D)

fun UnwrappedType.substitute(substitutor: TypeSubstitutor): UnwrappedType = substitutor.substitute(this, Variance.INVARIANT)!!.unwrap()


fun incorrectReceiver(callReceiver: SimpleCallArgument): Nothing =
        error("Incorrect receiver type: $callReceiver. Class name: ${callReceiver.javaClass.canonicalName}")