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

package org.jetbrains.jet.lang.resolve.typeBinding

import org.jetbrains.jet.lang.types.JetType
import org.jetbrains.jet.lang.descriptors.TypeParameterDescriptor
import com.intellij.psi.PsiElement
import org.jetbrains.jet.lang.psi.JetTypeElement
import org.jetbrains.jet.lang.psi.JetCallableDeclaration
import org.jetbrains.jet.lang.resolve.BindingTrace
import org.jetbrains.jet.lang.resolve.BindingContext
import org.jetbrains.jet.lang.psi.JetTypeReference
import java.util.AbstractList
import org.jetbrains.jet.lang.types.TypeProjection
import org.jetbrains.jet.lang.descriptors.CallableDescriptor
import com.intellij.psi.tree.TokenSet
import org.jetbrains.jet.lexer.JetTokens


trait TypeBinding<out P : PsiElement> {
    val psiElement: P
    val jetType: JetType
    fun getTypeArgs(): List<TypeArgumentBinding<P>>
}

trait TypeArgumentBinding<out P: PsiElement> {
    val typeProjection: TypeProjection
    val typeParameterDescriptor: TypeParameterDescriptor
    val typeBinding: TypeBinding<P>?
}

fun JetTypeReference.createTypeBinding(trace: BindingTrace): TypeBinding<JetTypeElement>? {
    val jetType = trace.get(BindingContext.TYPE, this)
    val psiElement = getTypeElement()
    if (jetType == null || psiElement == null)
        return null
    else
        return ExplicitTypeBinding(trace, psiElement, jetType)
}

fun JetCallableDeclaration.createTypeBindingForReturnType(trace: BindingTrace): TypeBinding<PsiElement>?  {
    val jetTypeReference = getReturnTypeRef()
    if (jetTypeReference != null)
        return jetTypeReference.createTypeBinding(trace)

    val descriptor = trace.get(BindingContext.DECLARATION_TO_DESCRIPTOR, this)
    if (descriptor !is CallableDescriptor) return null;

    val jetType = descriptor.getReturnType()
    return if(jetType == null) null else SillyTypeBinding(trace, this, jetType)
}

class TypeArgumentBindingImpl<out P: PsiElement>(
        override val typeProjection: TypeProjection,
        override val typeParameterDescriptor: TypeParameterDescriptor,
        override val typeBinding: TypeBinding<P>?
) : TypeArgumentBinding<P>

class ExplicitTypeBinding(val trace: BindingTrace, override val psiElement: JetTypeElement, override val jetType: JetType) : TypeBinding<JetTypeElement> {
    val constructor = jetType.getConstructor()
    val typeArgsSize = constructor.getParameters().size
    val isErrorBinding: Boolean
    {
        val sizeIsEqual = constructor.getParameters().size == jetType.getArguments().size
                        && constructor.getParameters().size == psiElement.getTypeArgumentsAsTypes().size
        isErrorBinding = jetType.isError() || !sizeIsEqual
    }


    override fun getTypeArgs(): List<TypeArgumentBinding<JetTypeElement>> {
        if (isErrorBinding) return listOf()

        return (0..typeArgsSize - 1).lazyMap {
            val typeProjection = jetType.getArguments().get(it)
            val typeParameterDescriptor = constructor.getParameters().get(it)
            val jetTypeElement = psiElement.getTypeArgumentsAsTypes().get(it)?.getTypeElement() // List<JetTypeReference?>

            val typeBinding = if (jetTypeElement == null) null else ExplicitTypeBinding(trace, jetTypeElement, typeProjection.getType())

            TypeArgumentBindingImpl(typeProjection, typeParameterDescriptor, typeBinding)
        }
    }
}

class SillyTypeBinding<out P : PsiElement>(val trace: BindingTrace, override val psiElement: P, override val jetType: JetType): TypeBinding<P> {
    val constructor = jetType.getConstructor()
    val isErrorBinding = jetType.isError() || constructor.getParameters().size != jetType.getArguments().size

    override fun getTypeArgs(): List<TypeArgumentBinding<P>> {
        if (isErrorBinding) return listOf()

        return (0..constructor.getParameters().size - 1).lazyMap {
            val typeProjection = jetType.getArguments().get(it)
            val typeParameterDescriptor = constructor.getParameters().get(it)

            val typeBinding = SillyTypeBinding(trace, psiElement, typeProjection.getType())
            TypeArgumentBindingImpl(typeProjection, typeParameterDescriptor, typeBinding)
        }
    }
}

val IntRange.size: Int
    get() = if (isEmpty()) 0 else end - start + 1;

fun <T> IntRange.lazyMap(f: (Int) -> T): List<T>
        = object : AbstractList<T>() {
    override fun get(index: Int): T = f(index + this@lazyMap.start)
    override fun size(): Int = this@lazyMap.size
}
