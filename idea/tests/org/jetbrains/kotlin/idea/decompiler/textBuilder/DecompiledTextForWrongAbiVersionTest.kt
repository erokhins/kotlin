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

package org.jetbrains.kotlin.idea.decompiler.textBuilder

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.decompiler.AbstractInternalCompiledClassesTest
import org.jetbrains.kotlin.idea.decompiler.JetClsFile
import org.jetbrains.kotlin.idea.decompiler.navigation.NavigateToDecompiledLibraryTest
import org.jetbrains.kotlin.idea.decompiler.stubBuilder.findClassFileByName
import org.jetbrains.kotlin.idea.test.JetJdkAndLibraryProjectDescriptor
import org.jetbrains.kotlin.test.JetTestUtils
import org.junit.Assert
import java.io.File

public class DecompiledTextForWrongAbiVersionTest : AbstractInternalCompiledClassesTest() {

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return JetJdkAndLibraryProjectDescriptor(File(JetTestUtils.getTestDataPathBase() + "/cli/jvm/wrongAbiVersionLib/bin"))
    }

    fun testSyntheticClassIsInvisibleWrongAbiVersion() = doTestNoPsiFilesAreBuiltForSyntheticClasses()

    fun testClassWithWrongAbiVersion() = doTest("ClassWithWrongAbiVersion")

    fun testPackageFacadeWithWrongAbiVersion() = doTest("WrongPackage")

    fun doTest(name: String) {
        val root = NavigateToDecompiledLibraryTest.findTestLibraryRoot(myModule!!)!!
        checkFileWithWrongAbiVersion(root.findClassFileByName(name))
    }

    private fun checkFileWithWrongAbiVersion(file: VirtualFile) {
        val psiFile = PsiManager.getInstance(getProject()!!).findFile(file)
        Assert.assertTrue(psiFile is JetClsFile)
        val decompiledText = psiFile!!.getText()!!
        Assert.assertTrue(decompiledText.contains(INCOMPATIBLE_ABI_VERSION_GENERAL_COMMENT))
    }
}
