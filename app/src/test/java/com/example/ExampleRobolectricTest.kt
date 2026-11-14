package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.config.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Skillect", appName)
    assertEquals("com.skillect.app", BuildConfig.APPLICATION_ID)
    assertEquals("com.skillect.app", context.packageName)
  }

  @Test
  fun `url validation blocks unsafe schemes and allows https`() {
    assertTrue(AppConfig.isValidHttpUrl("https://skillect.global/projects/1"))
    assertTrue(AppConfig.isValidHttpUrl("http://example.org"))
    assertFalse(AppConfig.isValidHttpUrl("javascript:alert(1)"))
    assertFalse(AppConfig.isValidHttpUrl("file:///etc/passwd"))
    assertFalse(AppConfig.isValidHttpUrl("not a url"))
    assertFalse(AppConfig.isValidHttpUrl(""))
  }
}
