package com.yshalsager.shizukushortcuts

import org.junit.Assert.assertTrue
import org.junit.Test

class PrivilegedStatusBarServiceTest {
    @Test
    fun `user service is an ibinder`() {
        assertTrue(IPrivilegedStatusBarService.Stub::class.java.isAssignableFrom(PrivilegedStatusBarService::class.java))
    }
}
