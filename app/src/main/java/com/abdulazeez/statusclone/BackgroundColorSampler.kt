package com.abdulazeez.statusclone

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.view.Display

sealed class SampleResult {
    data class Success(val color: Int) : SampleResult()
    object Throttled : SampleResult()
    object Failed : SampleResult()
}

/**
 * Real "fake transparency": instead of an actually-transparent overlay (which
 * would let the real system UI show through), this samples the actual
 * on-screen pixels just below our bar and paints our bar that same solid
 * color - opaque, but visually blended in.
 *
 * There is no public Android API that exposes a view's rendered color
 * directly, so screenshot sampling via AccessibilityService.takeScreenshot()
 * is the only honest way to do this - and that API only exists on Android 11+
 * (API 30). Below that, sample() always reports Failed and the caller should
 * use a fixed fallback color instead.
 */
class BackgroundColorSampler(private val service: AccessibilityService) {

    private var lastSampleTime = 0L
    private val minIntervalMs = 2500L

    fun sample(barHeightPx: Int, onResult: (SampleResult) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onResult(SampleResult.Failed)
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastSampleTime < minIntervalMs) {
            onResult(SampleResult.Throttled)
            return
        }
        lastSampleTime = now

        try {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        val bitmap = try {
                            Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                                ?.copy(Bitmap.Config.ARGB_8888, false)
                        } catch (_: Exception) {
                            null
                        } finally {
                            result.hardwareBuffer.close()
                        }
                        if (bitmap == null) {
                            onResult(SampleResult.Failed)
                            return
                        }
                        val color = averageColorBelowBar(bitmap, barHeightPx)
                        bitmap.recycle()
                        onResult(if (color != null) SampleResult.Success(color) else SampleResult.Failed)
                    }

                    override fun onFailure(errorCode: Int) {
                        // Common on secure lock screens (FLAG_SECURE) or right after rotation.
                        onResult(SampleResult.Failed)
                    }
                }
            )
        } catch (_: Exception) {
            onResult(SampleResult.Failed)
        }
    }

    /** Samples a thin strip just below our own bar so we never sample our own drawn pixels. */
    private fun averageColorBelowBar(bitmap: Bitmap, barHeightPx: Int): Int? {
        val sampleY = (barHeightPx + 4).coerceAtMost(bitmap.height - 1)
        if (sampleY < 0 || bitmap.width == 0) return null
        val step = (bitmap.width / 20).coerceAtLeast(1)
        var r = 0L
        var g = 0L
        var b = 0L
        var count = 0
        var x = 0
        while (x < bitmap.width) {
            val pixel = bitmap.getPixel(x, sampleY)
            r += Color.red(pixel)
            g += Color.green(pixel)
            b += Color.blue(pixel)
            count++
            x += step
        }
        if (count == 0) return null
        return Color.rgb((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
    }
}
