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
import org.jetbrains.k2js.translate.utils.TranslationUtils
import com.google.dart.compiler.backend.js.ast.JsInvocation
import org.jetbrains.k2js.translate.context.Namer
import com.google.dart.compiler.backend.js.ast.JsStringLiteral
import com.google.dart.compiler.backend.js.ast.JsArrayLiteral
import com.google.dart.compiler.backend.js.ast.JsLiteral
import org.jetbrains.jet.lang.psi.JetNamedFunction
import org.jetbrains.k2js.translate.utils.JsDescriptorUtils
import com.google.dart.compiler.backend.js.ast.JsDocComment
import com.google.dart.compiler.backend.js.ast.JsBlock

class FileTranslator private (val file: JetFile, val context: TranslationContext, val descriptor: NamespaceDescriptor) : AbstractTranslator(context) {
    class object {
        fun translateFile(file: JetFile, context: TranslationContext): JsStatement {
            val descriptor = context.bindingContext().get(BindingContext.FILE_TO_NAMESPACE, file)!!
            return FileTranslator(file, context, descriptor).translate()
        }
    }

    private val objectLiteral: JsObjectLiteral = JsObjectLiteral(true);
    private val visitor = FileDeclarationVisitor(context, objectLiteral.getPropertyInitializers()!!)

    private val definitionPlace = object : NotNullLazyValue<Trinity<List<JsPropertyInitializer>, LabelGenerator, JsExpression>>() {
        override fun compute(): Trinity<List<JsPropertyInitializer>, LabelGenerator, JsExpression> {
            LiteralFunctionTranslator.createPlace(objectLiteral.getPropertyInitializers()!!, context.getQualifiedReference(descriptor));
        }
    }

    private fun createStringLiteral(str: String): JsStringLiteral {
        return context.program().getStringLiteral(str)!!;
    }

    private fun getShortFileName(file: JetFile): String {
        val name = file.getName()
        val index = name.lastIndexOf('/')
        return if (index < 0)
            name
        else
            name.substring(index + 1)
    }

    private fun createPublicApiArray(): JsArrayLiteral {
        return JsArrayLiteral(visitor.publicApi.map { createStringLiteral(it.getIdent()!!) })
    }

    private fun createFileStatement(): JsStatement {
        val packageName = file.getPackageName() ?: ""
        val invocation = JsInvocation(context.namer().getAddPackagePart())

        val args = invocation.getArguments()!!
        args.add(JsNameRef(Namer.getRootNamespaceName()));
        args.add(createStringLiteral(packageName))
        args.add(visitor.computeInitializer() ?: JsLiteral.NULL)

        val packageRef = if (packageName == "") "_" else "_.$packageName"
        args.add(JsDocComment(JsAstUtils.LENDS_JS_DOC_TAG, packageRef))
        args.add(JsObjectLiteral(visitor.getResult(), true))
        return invocation.makeStmt()!!
    }

    fun translate(): JsStatement {
        context.literalFunctionTranslator().setDefinitionPlace(definitionPlace)
            for (declaration in file.getDeclarations()) {
                if (!AnnotationsUtils.isPredefinedObject(BindingUtils.getDescriptorForElement(bindingContext(), declaration))) {
                    declaration.accept(visitor, context)
                }
            }
        context.literalFunctionTranslator().setDefinitionPlace(null)
        return createFileStatement()
    }
}


class FileDeclarationVisitor(val context: TranslationContext, result: List<JsPropertyInitializer>) : DeclarationBodyVisitor() {
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

    private fun addIfPublicApi(descriptor: MemberDescriptor) {
        if (descriptor.getVisibility().isPublicAPI()) {
            publicApi.add(context.getNameForDescriptor(descriptor));
        }
    }

    private fun addToResult(descriptor: MemberDescriptor, value: JsExpression) {
        val entry = JsPropertyInitializer(context.getNameForDescriptor(descriptor).makeRef()!!, value)
        result.add(entry)
        addIfPublicApi(descriptor)
    }

    public override fun visitClass(expression: JetClass, context: TranslationContext?): Void? {
        val classDescriptor = getClassDescriptor(context!!.bindingContext(), expression)
        val value = ClassTranslator(expression, context).translate()
        addToResult(classDescriptor, value)
        return null
    }

    public override fun visitObjectDeclaration(declaration : JetObjectDeclaration, context : TranslationContext?) : Void? {
        val classDescriptor = getClassDescriptor(context!!.bindingContext(), declaration)
        InitializerUtils.generateObjectInitializer(declaration, initializerStatements, context)
        addToResult(classDescriptor, context.namer().getUndefinedExpression())
        return null
    }

    public override fun visitProperty(expression: JetProperty, context: TranslationContext?): Void? {
        val propertyDescriptor: PropertyDescriptor = getPropertyDescriptor(context!!.bindingContext(), expression)
        super.visitProperty(expression, context)
        val initializer = expression.getInitializer()
        if (initializer != null) {
            val value = Translation.translateAsExpression(initializer, initializerContext)
            initializerStatements.add(generateInitializerForProperty(context, propertyDescriptor, value))
        }

        val delegate = generateInitializerForDelegate(context, expression)
        if (delegate != null)
            initializerStatements.add(delegate)

        if (JsDescriptorUtils.isSimpleFinalProperty(propertyDescriptor)) { // for enother kind of property result has JsObjectLiteral for this property
            addToResult(propertyDescriptor, context.namer().getUndefinedExpression())
        } else {
            addIfPublicApi(propertyDescriptor)
        }
        return null
    }

    public override fun visitAnonymousInitializer(expression: JetClassInitializer, context: TranslationContext?): Void? {
        expression.accept(initializerVisitor, initializerContext)
        return null
    }

    override fun visitNamedFunction(expression: JetNamedFunction, context: TranslationContext?): Void? {
        super<DeclarationBodyVisitor>.visitNamedFunction(expression, context)

        addIfPublicApi(getFunctionDescriptor(context!!.bindingContext(), expression))
        return null;
    }

}
