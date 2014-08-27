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

package org.jetbrains.jet.lang.resolve.varianceChecker

import org.jetbrains.jet.lang.descriptors.ClassDescriptor
import org.jetbrains.jet.lang.descriptors.PropertyDescriptor
import org.jetbrains.jet.lang.diagnostics.Errors
import org.jetbrains.jet.lang.psi.JetProperty
import org.jetbrains.jet.lang.descriptors.TypeParameterDescriptor
import org.jetbrains.jet.lang.types.Variance
import org.jetbrains.jet.lang.types.checker.TypeCheckingProcedure.EnrichedProjectionKind.*
import org.jetbrains.jet.lang.types.checker.TypeCheckingProcedure.*
import org.jetbrains.jet.lang.diagnostics.Diagnostic
import org.jetbrains.jet.lang.descriptors.Visibilities
import org.jetbrains.jet.lang.resolve.BindingTrace
import org.jetbrains.jet.lang.psi.JetNamedFunction
import org.jetbrains.jet.lang.descriptors.SimpleFunctionDescriptor
import org.jetbrains.jet.lang.resolve.typeBinding.TypeBinding
import com.intellij.psi.PsiElement
import org.jetbrains.jet.lang.resolve.typeBinding.createTypeBinding
import org.jetbrains.jet.lang.resolve.typeBinding.createTypeBindingForReturnType
import org.jetbrains.jet.lang.psi.JetCallableDeclaration
import org.jetbrains.jet.lang.descriptors.MemberDescriptor
import org.jetbrains.jet.lang.descriptors.CallableDescriptor
import org.jetbrains.jet.lang.resolve.TopDownAnalysisContext
import org.jetbrains.jet.lang.types.JetType


class VarianceChecker(val trace: BindingTrace) {

    fun process(c: TopDownAnalysisContext) {
        check(c)
    }
    
    fun check(c: TopDownAnalysisContext) {
        checkFunctions(c)
        checkProperties(c)
    }
    
    private fun checkFunctions(c: TopDownAnalysisContext) {
        for (entry in c.getFunctions()!!.entrySet()) {
            checkFunction(entry.key, entry.value)
        }
    }
    private fun checkFunction(declaration: JetNamedFunction, descriptor: SimpleFunctionDescriptor) = declaration.checkVariance(descriptor, trace)

    private fun checkProperties(c: TopDownAnalysisContext) {
        for (entry in c.getProperties()!!.entrySet()) 
            checkProperty(entry.key, entry.value)
    }
    
    private fun checkProperty(declaration: JetProperty, descriptor: PropertyDescriptor) = declaration.checkVariance(descriptor, trace)
}


class SuperPrivateRecorder(val declaration: JetCallableDeclaration, val descriptor: CallableDescriptor, val trace: BindingTrace) : Function1<Diagnostic, Unit> {
    private val isPrivate = descriptor.getVisibility() == Visibilities.PRIVATE
    private var superPrivate = false

    override fun invoke(p1: Diagnostic) {
        if (!isPrivate)
            trace.report(p1)
        else {
            if (!superPrivate) {
                superPrivate = true
                record()
            }
        }
    }

    private fun record() {} // todo
}

fun JetProperty.checkVariance(propertyDescriptor: PropertyDescriptor, trace: BindingTrace) {
    val containerDescriptor = propertyDescriptor.getContainingDeclaration()
    if (containerDescriptor !is ClassDescriptor) return

    val recorder = SuperPrivateRecorder(this, propertyDescriptor, trace)
    checkReceiver(trace, recorder)
    checkTypeParameters(trace, recorder)

    val propertyTypeBinding = this.createTypeBindingForReturnType(trace)
    val position = if (propertyDescriptor.isVar()) TypePosition.INV else TypePosition.OUT
    propertyTypeBinding.checkTypePosition(position, recorder)
}

fun JetNamedFunction.checkVariance(functionDescriptor: SimpleFunctionDescriptor, trace: BindingTrace) {
    val containerDescriptor = functionDescriptor.getContainingDeclaration()
    if (containerDescriptor !is ClassDescriptor) return

    val recorder = SuperPrivateRecorder(this, functionDescriptor, trace)
    checkReceiver(trace, recorder)
    checkReturnType(trace, recorder)
    checkTypeParameters(trace, recorder)
    for (parameter in getValueParameters()) {
        parameter.getTypeReference()?.createTypeBinding(trace).checkTypePosition(TypePosition.IN, recorder)
    }
}

private fun JetCallableDeclaration.checkReceiver(trace: BindingTrace, report: (Diagnostic) -> Unit) {
    val receiverTypeBinding = this.getReceiverTypeRef()?.createTypeBinding(trace)
    receiverTypeBinding.checkTypePosition(TypePosition.IN, report)
}

private fun JetCallableDeclaration.checkReturnType(trace: BindingTrace, report: (Diagnostic) -> Unit) {
    val receiverTypeBinding = this.createTypeBindingForReturnType(trace)
    receiverTypeBinding.checkTypePosition(TypePosition.OUT, report)
}

private fun JetCallableDeclaration.checkTypeParameters(trace: BindingTrace, report: (Diagnostic) -> Unit) {
    for (typeParameter in getTypeParameters()) {
        val typeBinding = typeParameter.getExtendsBound()?.createTypeBinding(trace)
        typeBinding.checkTypePosition(TypePosition.IN, report)
    }
    for (typeConstraint in getTypeConstraints()) {
        val typeBinding = typeConstraint.getBoundTypeReference()?.createTypeBinding(trace)
        typeBinding.checkTypePosition(TypePosition.IN, report)
    }
}

enum class TypePosition(val name: String) {
    IN: TypePosition("in")
    OUT: TypePosition("out")
    INV: TypePosition("invariant")

    fun reverse(): TypePosition
            = when(this) {
        IN -> OUT
        OUT -> IN
        INV -> INV
    }

    fun allowsVariance(variance: Variance): Boolean
            = when(this) {
        IN -> variance.allowsInPosition()
        OUT -> variance.allowsOutPosition()
        INV -> variance.allowsInPosition() && variance.allowsOutPosition()
    }
}

fun TypeBinding<PsiElement>?.checkTypePosition(position: TypePosition, report: (Diagnostic) -> Unit) {
    if (this == null) return
    checkTypePosition(jetType, position, report)
}

private fun TypeBinding<PsiElement>.checkTypePosition(containingType: JetType, position: TypePosition, report: (Diagnostic) -> Unit) {
    val typeDescriptor = jetType.getConstructor().getDeclarationDescriptor()
    if (typeDescriptor != null && typeDescriptor is TypeParameterDescriptor) {
        val declarationVariance = typeDescriptor.getVariance()
        if (!position.allowsVariance(declarationVariance)) {
            val data = VarianceConflictDiagnostic(containingType, jetType, declarationVariance.getLabel(), position.name)
            report(Errors.TYPE_PARAMETER_VARIANCE_CONFLICT.on(psiElement, data))
        }
        return
    }

    for (bindingArgument in getTypeArgs()) {
        val projectionKind = getEffectiveProjectionKind(bindingArgument.typeParameterDescriptor, bindingArgument.typeProjection)
        val newPosition = when (projectionKind) {
            OUT -> position
            IN -> position.reverse()
            INV -> TypePosition.INV
            else -> null // see Errors.CONFLICTING_PROJECTION
        }
        if (newPosition != null)
            bindingArgument.typeBinding?.checkTypePosition(containingType, newPosition, report)
    }
}

class VarianceConflictDiagnostic(val containingType: JetType,
                         val typeParameter: JetType,
                         val declarationPosition: String,
                         val useSidePosition: String)

private fun MemberDescriptor.isPrivate() = getVisibility() == Visibilities.PRIVATE