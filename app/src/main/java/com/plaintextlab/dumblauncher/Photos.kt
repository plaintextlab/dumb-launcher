package com.plaintextlab.dumblauncher

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size

/** Recent photos for the Photos live tile, read from the phone's gallery (MediaStore). */
object Photos {
    fun enabled(c: Context) = Prefs.getBool(c, Prefs.SHOW_PHOTOS, true)

    val permission: String
        get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
                else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasPermission(c: Context) = c.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /** The newest [max] photos, newest first. */
    fun recent(c: Context, max: Int = 12): List<Uri> {
        if (!hasPermission(c)) return emptyList()
        val base = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        return try {
            c.contentResolver.query(base, arrayOf(MediaStore.Images.Media._ID), null, null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cur ->
                val out = ArrayList<Uri>()
                while (cur.moveToNext() && out.size < max) out += ContentUris.withAppendedId(base, cur.getLong(0))
                out
            }.orEmpty()
        } catch (e: Exception) { emptyList() }
    }

    /** A small copy of the photo, about [px] on its short side (background thread). */
    fun thumb(c: Context, uri: Uri, px: Int): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 29) c.contentResolver.loadThumbnail(uri, Size(px * 2, px), null)
        else {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var s = 1
            while (minOf(o.outWidth, o.outHeight) / (s * 2) >= px) s *= 2
            c.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s })
            }
        }
    } catch (e: Exception) { null }
}
