package com.antlers.support.lsp

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.platform.lsp.api.customization.LspCompletionSupport
import com.intellij.testFramework.LightVirtualFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AntlersLspServerDescriptorTest {
    @Test
    fun removesUnsupportedProjectDetailsClientRequest() {
        val script =
            """function mT(t){ee.instance?.setStructuredProject(t),X1(t);let e={content:t};Fe.sendRequest("antlers/projectDetailsAvailable",e)}"""

        val patched = AntlersLspServerDescriptor.prepareBundledServerScript(script)

        assertFalse(patched.contains("antlers/projectDetailsAvailable"))
        assertTrue(patched.contains("setStructuredProject"))
        assertTrue(patched.contains("X1(t)"))
    }

    @Test
    fun leavesOtherRequestsUntouched() {
        val script =
            """Fe.sendRequest("workspace/applyEdit",e);Fe.sendRequest("antlers/projectUpdate",e)"""

        val patched = AntlersLspServerDescriptor.prepareBundledServerScript(script)

        assertEquals(script, patched)
    }

    @Test
    fun disablesLspCompletionToAvoidDuplicateAntlersSuggestions() {
        val method = AntlersLspServerDescriptor.DISABLED_COMPLETION_SUPPORT.javaClass
            .getDeclaredMethod("shouldRunCodeCompletion", CompletionParameters::class.java)

        assertTrue(
            method.declaringClass != LspCompletionSupport::class.java
        )
    }

    @Test
    fun prefersNativeFormatterOverExclusiveLspFormatting() {
        assertFalse(
            AntlersLspServerDescriptor.NATIVE_FORMATTING_SUPPORT.shouldFormatThisFileExclusivelyByServer(
                LightVirtualFile("demo.antlers.html", ""),
                false,
                true
            )
        )
    }
}
