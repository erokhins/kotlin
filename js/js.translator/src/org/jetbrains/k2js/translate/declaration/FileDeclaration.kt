/*
 * Copyright 2010-2013 JetBrains s.r.o.
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
package org.jetbrains.k2js.translate.declaration

import com.google.dart.compiler.backend.js.ast.JsExpression
import com.google.dart.compiler.backend.js.ast.JsNameRef
import com.google.dart.compiler.backend.js.ast.JsPropertyInitializer
import org.jetbrains.k2js.translate.initializer.InitializerVisitor
import org.jetbrains.k2js.translate.utils.JsAstUtils
import org.jetbrains.k2js.translate.context.TranslationContext
import org.jetbrains.jet.lang.psi.JetClassInitializer
import com.google.dart.compiler.backend.js.ast.JsStatement
import org.jetbrains.k2js.translate.general.Translation
import org.jetbrains.jet.lang.psi.JetProperty
import org.jetbrains.k2js.translate.initializer.InitializerUtils
import org.jetbrains.jet.lang.psi.JetObjectDeclaration
import org.jetbrains.jet.lang.psi.JetClass
import com.google.dart.compiler.backend.js.ast.JsFunction
import org.jetbrains.k2js.translate.utils.BindingUtils.*
import org.jetbrains.k2js.translate.initializer.InitializerUtils.*
import org.jetbrains.jet.lang.descriptors.PropertyDescriptor
import com.google.dart.compiler.backend.js.ast.JsName
import org.jetbrains.jet.lang.descriptors.MemberDescriptor
import org.jetbrains.k2js.translate.general.AbstractTranslator
import org.jetbrains.jet.lang.psi.JetFile
import com.intellij.util.SmartList
import com.google.dart.compiler.backend.js.ast.JsVisitor
import com.google.dart.compiler.backend.js.ast.JsNode
import com.google.dart.compiler.backend.js.ast.JsEmpty
import com.google.dart.compiler.backend.js.ast.JsBlock
import org.jetbrains.jet.lang.descriptors.NamespaceDescriptor
import org.jetbrains.jet.lang.resolve.BindingContext
import com.google.dart.compiler.backend.js.ast.JsObjectLiteral
import com.google.dart.compiler.backend.js.ast.JsExpression
import org.jetbrains.k2js.translate.LabelGenerator
import com.intellij.openapi.util.Trinity
import com.intellij.openapi.util.NotNullLazyValue
import org.jetbrains.k2js.translate.expression.LiteralFunctionTranslator
import org.jetbrains.k2js.translate.utils.BindingUtils
import org.jetbrains.k2js.translate.utils.AnnotationsUtils
import com.google.dart.compiler.backend.js.ast.JsScope
import org.jetbrains.k2js.translate.utils.TranslationUtils


class TranslatedFile(val name: JsName, val qualifier: JsNameRef, val fileStatement: JsStatement, val publicProperties: List<JsName>)

class FileTranslator private (val file: JetFile, val context: TranslationContext, val descriptor: NamespaceDescriptor) : AbstractTranslator(context) {
    class object {
        fun translateFile(file: JetFile, context: TranslationContext) : TranslatedFile {
            val descriptor = context.bindingContext().get(BindingContext.FILE_TO_NAMESPACE, file)!!
            return FileTranslator(file, context, descriptor).translate()
        }
    }

    private val objectLiteral: JsObjectLiteral = JsObjectLiteral(true);
    private val visitor = FileDeclarationVisitor(context)

    private val definitionPlace = object : NotNullLazyValue<Trinity<List<JsPropertyInitializer>, LabelGenerator, JsExpression>>() {
        override fun compute(): Trinity<List<JsPropertyInitializer>, LabelGenerator, JsExpression> {
            LiteralFunctionTranslator.createPlace(objectLiteral.getPropertyInitializers()!!, context.getQualifiedReference(descriptor));
        }
    }

    private fun createFileStatement(): JsStatement {
        throw UnsupportedOperationException()
    }

    fun translate(): TranslatedFile {
        context.literalFunctionTranslator().setDefinitionPlace(definitionPlace)
            for (declaration in file.getDeclarations()) {
                if (!AnnotationsUtils.isPredefinedObject(BindingUtils.getDescriptorForElement(bindingContext(), declaration))) {
                    declaration.accept(visitor, context)
                }
            }
        context.literalFunctionTranslator().setDefinitionPlace(null)
        val fullQualifer = context.getQualifiedReference(descriptor)
        val fileName = context.scope().declareName(TranslationUtils.getMangledName(file))!!
        return TranslatedFile(fileName, fullQualifer, createFileStatement(), visitor.publicApi)
    }
}


class FileDeclarationVisitor(val context: TranslationContext) : DeclarationBodyVisitor() {
    private val initializer = JsAstUtils.createFunctionWithEmptyBody(context.scope())
    private val initializerContext = context.contextWithScope(initializer)
    private val initializerStatements = initializer.getBody()!!.getStatements()!!
    private val initializerVisitor = InitializerVisitor(initializerStatements)

    val publicApi : MutableList<JsName> = SmartList<JsName>();

    fun computeInitializer(): JsFunction? {
        if (initializerStatements.isEmpty()) {
            return null
        } else {
            return initializer
        }
    }

    fun addIfPublicApi(descriptor: MemberDescriptor) {
        if (descriptor.getVisibility().isPublicAPI()) {
            publicApi.add(context.getNameForDescriptor(descriptor));
        }
    }

    public override fun visitClass(expression: JetClass, context: TranslationContext?): Void? {
        val classDescriptor = getClassDescriptor(context!!.bindingContext(), expression)
        val value = ClassTranslator(expression, context).translate()
        val entry = JsPropertyInitializer(context.getNameForDescriptor(classDescriptor).makeRef()!!, value)
        result.add(entry)

        addIfPublicApi(classDescriptor)
        return null
    }

    public override fun visitObjectDeclaration(declaration: JetObjectDeclaration, context: TranslationContext?): Void? {
        InitializerUtils.generateObjectInitializer(declaration, initializerStatements, context!!)
        addIfPublicApi(getClassDescriptor(context.bindingContext(), declaration))
        return null
    }

    public override fun visitProperty(expression: JetProperty, context: TranslationContext?): Void? {
        context!! // hack

        super.visitProperty(expression, context)
        val initializer = expression.getInitializer()
        if (initializer != null) {
            val value = Translation.translateAsExpression(initializer, initializerContext)
            val propertyDescriptor: PropertyDescriptor = getPropertyDescriptor(context.bindingContext(), expression)
            initializerStatements.add(generateInitializerForProperty(context, propertyDescriptor, value))
        }

        val delegate = generateInitializerForDelegate(context, expression)
        if (delegate != null)
            initializerStatements.add(delegate)

        addIfPublicApi(getPropertyDescriptor(context.bindingContext(), expression))
        return null
    }

    public override fun visitAnonymousInitializer(expression: JetClassInitializer, context: TranslationContext?): Void? {
        expression.accept(initializerVisitor, initializerContext)
        return null
    }

}
