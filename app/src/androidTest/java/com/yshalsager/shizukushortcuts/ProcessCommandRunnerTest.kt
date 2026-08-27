package com.yshalsager.shizukushortcuts

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProcessCommandRunnerTest {
    @Test(timeout = 7_000)
    fun output_and_duration_are_bounded() {
        val completed = ProcessCommandRunner.run_command(listOf("sh", "-c", "yes | head -c 70000"))
        val timed_out = ProcessCommandRunner.run_command(listOf("sh", "-c", "trap '' TERM; exec yes"))

        assertFalse(completed.timed_out)
        assertTrue(completed.output.toByteArray().size <= 64 * 1_024)
        assertTrue(timed_out.timed_out)
        assertTrue(timed_out.output.toByteArray().size <= 64 * 1_024)
    }
}
