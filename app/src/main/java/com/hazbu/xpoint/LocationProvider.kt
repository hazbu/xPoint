package com.hazbu.xpoint

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

class LocationProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val prefs = context?.getSharedPreferences("fake_location_prefs", Context.MODE_PRIVATE)
        val cursor = MatrixCursor(arrayOf("lat", "long"))
        cursor.addRow(arrayOf(
            prefs?.getString("lat", "0.0") ?: "0.0",
            prefs?.getString("long", "0.0") ?: "0.0"
        ))
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
