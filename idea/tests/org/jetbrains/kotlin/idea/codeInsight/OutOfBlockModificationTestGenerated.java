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

package org.jetbrains.kotlin.idea.codeInsight;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.test.JUnit3RunnerWithInners;
import org.jetbrains.kotlin.test.JetTestUtils;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.regex.Pattern;

/** This class is generated by {@link org.jetbrains.kotlin.generators.tests.TestsPackage}. DO NOT MODIFY MANUALLY */
@SuppressWarnings("all")
@TestMetadata("idea/testData/codeInsight/outOfBlock")
@TestDataPath("$PROJECT_ROOT")
@RunWith(JUnit3RunnerWithInners.class)
public class OutOfBlockModificationTestGenerated extends AbstractOutOfBlockModificationTest {
    public void testAllFilesPresentInOutOfBlock() throws Exception {
        JetTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("idea/testData/codeInsight/outOfBlock"), Pattern.compile("^(.+)\\.kt$"), true);
    }

    @TestMetadata("Class_Class_FunNoType_Block.kt")
    public void testClass_Class_FunNoType_Block() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("idea/testData/codeInsight/outOfBlock/Class_Class_FunNoType_Block.kt");
        doTest(fileName);
    }

    @TestMetadata("Class_Class_FunNoType_Block_Expression.kt")
    public void testClass_Class_FunNoType_Block_Expression() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("idea/testData/codeInsight/outOfBlock/Class_Class_FunNoType_Block_Expression.kt");
        doTest(fileName);
    }

    @TestMetadata("FunInFun.kt")
    public void testFunInFun() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("idea/testData/codeInsight/outOfBlock/FunInFun.kt");
        doTest(fileName);
    }

    @TestMetadata("FunNoBody.kt")
    public void testFunNoBody() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("idea/testData/codeInsight/outOfBlock/FunNoBody.kt");
        doTest(fileName);
    }

    @TestMetadata("FunNoType_Block.kt")
    public void testFunNoType_Block() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("idea/testData/codeInsight/outOfBlock/FunNoType_Block.kt");
        doTest(fileName);
    }

    @TestMetadata("FunNoType_Block_Class.kt")
    public void testFunNoType_Block_Class() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("idea/testData/codeInsight/outOfBlock/FunNoType_Block_Class.kt");
        doTest(fileName);
    }

    @TestMetadata("FunWithType_Initializer.kt")
    public void testFunWithType_Initializer() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("idea/testData/codeInsight/outOfBlock/FunWithType_Initializer.kt");
        doTest(fileName);
    }

    @TestMetadata("FunWithType_Initializer_Expression.kt")
    public void testFunWithType_Initializer_Expression() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("idea/testData/codeInsight/outOfBlock/FunWithType_Initializer_Expression.kt");
        doTest(fileName);
    }

    @TestMetadata("InAntonymsObjectDeclaration.kt")
    public void testInAntonymsObjectDeclaration() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("idea/testData/codeInsight/outOfBlock/InAntonymsObjectDeclaration.kt");
        doTest(fileName);
    }

    @TestMetadata("InClass.kt")
    public void testInClass() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("idea/testData/codeInsight/outOfBlock/InClass.kt");
        doTest(fileName);
    }

    @TestMetadata("InClassInClass.kt")
    public void testInClassInClass() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("idea/testData/codeInsight/outOfBlock/InClassInClass.kt");
        doTest(fileName);
    }

    @TestMetadata("InClassPropertyAccessor.kt")
    public void testInClassPropertyAccessor() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("idea/testData/codeInsight/outOfBlock/InClassPropertyAccessor.kt");
        doTest(fileName);
    }

    @TestMetadata("InFunInFunWithBody.kt")
    public void testInFunInFunWithBody() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("idea/testData/codeInsight/outOfBlock/InFunInFunWithBody.kt");
        doTest(fileName);
    }

    @TestMetadata("InFunInFunctionInitializerInFun.kt")
    public void testInFunInFunctionInitializerInFun() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("idea/testData/codeInsight/outOfBlock/InFunInFunctionInitializerInFun.kt");
        doTest(fileName);
    }

    @TestMetadata("InFunInMultiDeclaration.kt")
    public void testInFunInMultiDeclaration() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("idea/testData/codeInsight/outOfBlock/InFunInMultiDeclaration.kt");
        doTest(fileName);
    }

    @TestMetadata("InFunInProperty.kt")
    public void testInFunInProperty() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("idea/testData/codeInsight/outOfBlock/InFunInProperty.kt");
        doTest(fileName);
    }

    @TestMetadata("InFunInPropertyInObjectLiteral.kt")
    public void testInFunInPropertyInObjectLiteral() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("idea/testData/codeInsight/outOfBlock/InFunInPropertyInObjectLiteral.kt");
        doTest(fileName);
    }

    @TestMetadata("InFunObjectLiteral.kt")
    public void testInFunObjectLiteral() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("idea/testData/codeInsight/outOfBlock/InFunObjectLiteral.kt");
        doTest(fileName);
    }

    @TestMetadata("InFunWithInference.kt")
    public void testInFunWithInference() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("idea/testData/codeInsight/outOfBlock/InFunWithInference.kt");
        doTest(fileName);
    }

    @TestMetadata("InFunctionLiteral.kt")
    public void testInFunctionLiteral() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("idea/testData/codeInsight/outOfBlock/InFunctionLiteral.kt");
        doTest(fileName);
    }

    @TestMetadata("InGlobalPropertyWithGetter.kt")
    public void testInGlobalPropertyWithGetter() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("idea/testData/codeInsight/outOfBlock/InGlobalPropertyWithGetter.kt");
        doTest(fileName);
    }

    @TestMetadata("InMethod.kt")
    public void testInMethod() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("idea/testData/codeInsight/outOfBlock/InMethod.kt");
        doTest(fileName);
    }

    @TestMetadata("InPropertyAccessorWithInference.kt")
    public void testInPropertyAccessorWithInference() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("idea/testData/codeInsight/outOfBlock/InPropertyAccessorWithInference.kt");
        doTest(fileName);
    }

    @TestMetadata("InPropertyWithFunctionLiteral.kt")
    public void testInPropertyWithFunctionLiteral() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("idea/testData/codeInsight/outOfBlock/InPropertyWithFunctionLiteral.kt");
        doTest(fileName);
    }

    @TestMetadata("InPropertyWithInference.kt")
    public void testInPropertyWithInference() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("idea/testData/codeInsight/outOfBlock/InPropertyWithInference.kt");
        doTest(fileName);
    }

    @TestMetadata("Object_FunNoType_Block.kt")
    public void testObject_FunNoType_Block() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("idea/testData/codeInsight/outOfBlock/Object_FunNoType_Block.kt");
        doTest(fileName);
    }

    @TestMetadata("Object_FunNoType_Block_Expression.kt")
    public void testObject_FunNoType_Block_Expression() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("idea/testData/codeInsight/outOfBlock/Object_FunNoType_Block_Expression.kt");
        doTest(fileName);
    }

    @TestMetadata("PropNotNullType_Initializer_ObjectLiteral_Fun.kt")
    public void testPropNotNullType_Initializer_ObjectLiteral_Fun() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("idea/testData/codeInsight/outOfBlock/PropNotNullType_Initializer_ObjectLiteral_Fun.kt");
        doTest(fileName);
    }

    @TestMetadata("PropNullType_Initializer_If_Fun.kt")
    public void testPropNullType_Initializer_If_Fun() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("idea/testData/codeInsight/outOfBlock/PropNullType_Initializer_If_Fun.kt");
        doTest(fileName);
    }

    @TestMetadata("PropNullType_Initializer_ObjectLiteral_Fun.kt")
    public void testPropNullType_Initializer_ObjectLiteral_Fun() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("idea/testData/codeInsight/outOfBlock/PropNullType_Initializer_ObjectLiteral_Fun.kt");
        doTest(fileName);
    }

    @TestMetadata("PropertyNoType_Initializer_String.kt")
    public void testPropertyNoType_Initializer_String() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("idea/testData/codeInsight/outOfBlock/PropertyNoType_Initializer_String.kt");
        doTest(fileName);
    }

    @TestMetadata("PropertyWithType_Initializer_String.kt")
    public void testPropertyWithType_Initializer_String() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("idea/testData/codeInsight/outOfBlock/PropertyWithType_Initializer_String.kt");
        doTest(fileName);
    }
}
