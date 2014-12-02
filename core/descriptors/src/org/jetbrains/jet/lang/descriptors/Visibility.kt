/*
 * Copyright 2010-2014 JetBrains s.r.o.
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

package org.jetbrains.jet.lang.descriptors

import org.jetbrains.jet.lang.resolve.scopes.receivers.ReceiverValue
import javax.swing.UIDefaults
import org.jetbrains.jet.storage.NotNullLazyValue
import java.beans

public trait Visibility {
    public val isPublicAPI: Boolean
    public val name: String

    /**
     * @return null if the answer is unknown
     */
    fun compareTo(other: Visibility): Int? = Visibilities.compareLocal(normalize(), other)

    public fun normalize(): Visibility = this

    protected fun isVisible(receiver: ReceiverValue, what: DeclarationDescriptorWithVisibility, from: DeclarationDescriptor): Boolean

    override fun toString() = name
}


abstract class AbstractVisibility(override val name: String, override val isPublicAPI: Boolean): Visibility
