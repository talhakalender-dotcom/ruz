package com.talha.ruz.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NanoTestCasesTest {
    @Test
    fun everyTaskHasBothInstructionLanguages() {
        val expected = (NanoTestCases.notes.size + NanoTestCases.questions.size + NanoTestCases.dumps.size) * 2
        assertEquals(expected, NanoTestCases.all.size)
    }

    @Test
    fun promptsContainTheirInput() {
        NanoTestCases.all.forEach { assertTrue(it.prompt.contains(it.input), "İstem girdiyi içermeli: ${it.input}") }
    }

    @Test
    fun promptsStayShortForNanoContext() {
        // Nano'nun bağlamı ~4000 token; Türkçe için kaba üst sınır olarak 3000 karakter.
        NanoTestCases.all.forEach { assertTrue(it.prompt.length < 3000) }
    }
}
