package pl.slqpl.rodzina.ui

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Screenshots contain synthetic fixtures only and are kept outside source control. */
fun captureUiPreview(name: String) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.waitForIdleSync()
    Thread.sleep(700) // Allow the native Maps surface and window compositor to present their frame.
    val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
    val destination = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-redesign/$name.png")
    destination.parentFile?.mkdirs()
    destination.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
}
