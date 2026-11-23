package com.tony.nosotros.presence

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.Base64
import java.io.ByteArrayOutputStream

/** Only the icon of the voluntarily shared foreground app. No app inventory or external URL. */
object AppIcons {
    fun capture(context: Context, packageName: String): String = runCatching {
        val drawable = context.packageManager.getApplicationIcon(packageName)
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        try {
            drawable.setBounds(0, 0, 32, 32); drawable.draw(Canvas(bitmap))
            val bytes = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray()
            }
            Base64.encodeToString(bytes, Base64.NO_WRAP).takeIf { it.length <= 8192 }.orEmpty()
        } finally { bitmap.recycle() }
    }.getOrDefault("")

    fun decode(icon: String): Bitmap? = runCatching {
        if (icon.length !in 1..8192 || !icon.startsWith("iVBORw0KGgo")) return null
        val bytes = Base64.decode(icon, Base64.NO_WRAP)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth !in 1..64 || options.outHeight !in 1..64) return null
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}
