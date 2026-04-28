package dev.bikram.remember.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() {
        val targetPackageName = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .packageName

        baselineProfileRule.collect(packageName = targetPackageName) {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()

            runCatching {
                val scrollableContent = UiScrollable(UiSelector().scrollable(true))
                if (scrollableContent.exists()) {
                    scrollableContent.flingToEnd(3)
                    scrollableContent.flingToBeginning(3)
                }
            }
        }
    }
}
