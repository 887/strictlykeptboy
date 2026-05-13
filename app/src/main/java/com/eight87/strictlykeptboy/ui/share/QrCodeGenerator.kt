package com.eight87.strictlykeptboy.ui.share

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Phase RR.2 — QR code generation for share links.
 *
 * Wraps ZXing core (Apache-2.0) into a narrow facade. Callers pass the
 * share-link URI string and receive an Android [Bitmap] suitable for
 * display in Compose via `Image(bitmap.asImageBitmap(), ...)`.
 *
 * SOLID.S: one job — render UTF-8 text into a QR bitmap. SOLID.D: callers
 * depend on the small [QrCodeGenerator] facade, not directly on ZXing's
 * generic BarcodeWriter surface. SOLID.I: the public API is a single
 * `encode()` call; no leak of QRCodeWriter or BitMatrix.
 *
 * Pure encode is exposed for unit-testing as [encodeMatrix] (no Android
 * deps), with [encode] doing the Bitmap conversion on top. This keeps
 * the Bitmap-free codec path testable under plain JVM tests.
 */
object QrCodeGenerator {

    /** Default render size in pixels. Caller can override for higher-DPI displays. */
    const val DEFAULT_SIZE_PX = 512

    /** Quiet-zone margin (modules around the symbol). 4 is the spec default. */
    private const val QUIET_ZONE = 4

    /**
     * Encode [content] as a square QR bitmap, [sizePx] on a side. Dark
     * modules are black, light modules are white. Throws
     * `IllegalArgumentException` if the content cannot fit in a QR code
     * at the requested size.
     */
    fun encode(content: String, sizePx: Int = DEFAULT_SIZE_PX): Bitmap {
        require(content.isNotEmpty()) { "QR content must not be empty" }
        require(sizePx >= 64) { "QR size must be at least 64px (was $sizePx)" }
        val matrix = encodeMatrix(content, sizePx)
        return bitmapFromMatrix(matrix)
    }

    /**
     * Pure (Android-free) encode for unit tests. Returns the ZXing
     * [BitMatrix] at [sizePx] × [sizePx]; the caller renders to whatever
     * surface is appropriate.
     */
    fun encodeMatrix(content: String, sizePx: Int = DEFAULT_SIZE_PX): BitMatrix {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to QUIET_ZONE,
        )
        return QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    }

    private fun bitmapFromMatrix(matrix: BitMatrix): Bitmap {
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val rowOffset = y * w
            for (x in 0 until w) {
                pixels[rowOffset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(pixels, 0, w, 0, 0, w, h)
        return bmp
    }
}
