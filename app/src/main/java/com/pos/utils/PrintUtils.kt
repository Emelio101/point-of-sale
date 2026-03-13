package com.pos.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.core.graphics.scale

object PrintUtils {
    fun decodeBitmapToEscPos(originalBitmap: Bitmap): ByteArray {
        val printerWidth = 384
        val maxLogoWidth = 200

        // 1. Calculate new dimensions while maintaining the image aspect ratio
        val scale = maxLogoWidth.toFloat() / originalBitmap.width
        val scaledHeight = (originalBitmap.height * scale).toInt()
        val scaledLogo = originalBitmap.scale(maxLogoWidth, scaledHeight)

        // 2. Create a full-width blank canvas to force perfect printer centering
        val centeredBitmap = createBitmap(printerWidth, scaledHeight)
        val canvas = Canvas(centeredBitmap)
        canvas.drawColor(Color.WHITE) // Fill background with solid white

        // 3. Draw the scaled logo exactly in the middle of our new blank canvas
        val leftMargin = (printerWidth - maxLogoWidth) / 2f
        canvas.drawBitmap(scaledLogo, leftMargin, 0f, null)

        // 4. Convert the centered canvas into ESC/POS monochrome bytes
        val width = centeredBitmap.width
        val height = centeredBitmap.height
        val widthBytes = (width + 7) / 8
        val data = ByteArray(8 + widthBytes * height)

        data[0] = 0x1D
        data[1] = 0x76
        data[2] = 0x30
        data[3] = 0x00
        data[4] = (widthBytes % 256).toByte()
        data[5] = (widthBytes / 256).toByte()
        data[6] = (height % 256).toByte()
        data[7] = (height / 256).toByte()

        var index = 8
        for (y in 0 until height) {
            for (x in 0 until widthBytes) {
                var byte = 0
                for (b in 0..7) {
                    val px = x * 8 + b
                    if (px < width) {
                        val pixel = centeredBitmap[px, y]
                        val r = Color.red(pixel)
                        val g = Color.green(pixel)
                        val bCol = Color.blue(pixel)
                        val a = Color.alpha(pixel)

                        val luminance = (0.299 * r + 0.587 * g + 0.114 * bCol).toInt()

                        // Dark pixel threshold
                        if (a > 128 && luminance < 128) {
                            byte = byte or (1 shl (7 - b))
                        }
                    }
                }
                data[index++] = byte.toByte()
            }
        }
        return data
    }
}