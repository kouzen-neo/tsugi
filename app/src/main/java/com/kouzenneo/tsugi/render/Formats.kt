package com.kouzenneo.tsugi.render

import android.graphics.Bitmap
import android.os.Build
import com.kouzenneo.tsugi.core.OutFormat

/** The encoder that actually backs each output format on this device. */
fun OutFormat.compressFormat(): Bitmap.CompressFormat = when (this) {
    OutFormat.JPEG -> Bitmap.CompressFormat.JPEG
    OutFormat.PNG -> Bitmap.CompressFormat.PNG
    OutFormat.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Bitmap.CompressFormat.WEBP_LOSSY
    } else {
        @Suppress("DEPRECATION")
        Bitmap.CompressFormat.WEBP
    }
}
