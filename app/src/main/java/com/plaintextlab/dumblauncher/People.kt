package com.plaintextlab.dumblauncher

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.ContactsContract

/** Contact photos for the People live tile: starred contacts first, then A-Z, so it stays put. */
object People {
    const val permission = Manifest.permission.READ_CONTACTS

    fun hasPermission(c: Context) = c.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    data class Face(val name: String, val photo: Bitmap)

    /** Up to [max] contacts that have a photo (background thread). */
    fun faces(c: Context, max: Int): List<Face> {
        if (!hasPermission(c)) return emptyList()
        val cr = c.contentResolver
        return try {
            cr.query(ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME),
                "${ContactsContract.Contacts.PHOTO_ID} IS NOT NULL", null,
                "${ContactsContract.Contacts.STARRED} DESC, ${ContactsContract.Contacts.DISPLAY_NAME} COLLATE NOCASE ASC")?.use { cur ->
                val out = ArrayList<Face>()
                while (cur.moveToNext() && out.size < max) {
                    val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, cur.getLong(0))
                    val bmp = ContactsContract.Contacts.openContactPhotoInputStream(cr, uri, false)?.use { BitmapFactory.decodeStream(it) }
                    if (bmp != null) out += Face(cur.getString(1).orEmpty(), bmp)
                }
                out
            }.orEmpty()
        } catch (e: Exception) { emptyList() }
    }
}
