package com.hazbu.xpoint
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.hazbu.xpoint.Constants.DEFAULT_ACCURACY_MAX
import com.hazbu.xpoint.Constants.DEFAULT_ACCURACY_MIN
import com.hazbu.xpoint.Constants.DEFAULT_JITTER_RADIUS
import com.hazbu.xpoint.Constants.DEFAULT_LAT
import com.hazbu.xpoint.Constants.DEFAULT_LONG
import com.hazbu.xpoint.Constants.DEFAULT_V_ACCURACY_MAX
import com.hazbu.xpoint.Constants.DEFAULT_V_ACCURACY_MIN
import com.hazbu.xpoint.Constants.KEY_ACCURACY_MAX
import com.hazbu.xpoint.Constants.KEY_ACCURACY_MIN
import com.hazbu.xpoint.Constants.KEY_JITTER_RADIUS
import com.hazbu.xpoint.Constants.KEY_LAT
import com.hazbu.xpoint.Constants.KEY_LONG
import com.hazbu.xpoint.Constants.KEY_V_ACCURACY_MAX
import com.hazbu.xpoint.Constants.KEY_V_ACCURACY_MIN
import com.hazbu.xpoint.Constants.PREFS_NAME
class LocationProvider : ContentProvider() {
    override fun onCreate(): Boolean = true
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cursor = MatrixCursor(arrayOf(
            KEY_LAT, KEY_LONG, KEY_ACCURACY_MIN, KEY_ACCURACY_MAX, KEY_JITTER_RADIUS,
            KEY_V_ACCURACY_MIN, KEY_V_ACCURACY_MAX
        ))
        cursor.addRow(arrayOf(
            prefs?.getString(KEY_LAT, DEFAULT_LAT.toString()) ?: DEFAULT_LAT.toString(),
            prefs?.getString(KEY_LONG, DEFAULT_LONG.toString()) ?: DEFAULT_LONG.toString(),
            prefs?.getFloat(KEY_ACCURACY_MIN, DEFAULT_ACCURACY_MIN)?.toString() ?: DEFAULT_ACCURACY_MIN.toString(),
            prefs?.getFloat(KEY_ACCURACY_MAX, DEFAULT_ACCURACY_MAX)?.toString() ?: DEFAULT_ACCURACY_MAX.toString(),
            prefs?.getFloat(KEY_JITTER_RADIUS, DEFAULT_JITTER_RADIUS)?.toString() ?: DEFAULT_JITTER_RADIUS.toString(),
            prefs?.getFloat(KEY_V_ACCURACY_MIN, DEFAULT_V_ACCURACY_MIN)?.toString() ?: DEFAULT_V_ACCURACY_MIN.toString(),
            prefs?.getFloat(KEY_V_ACCURACY_MAX, DEFAULT_V_ACCURACY_MAX)?.toString() ?: DEFAULT_V_ACCURACY_MAX.toString()
        ))
        return cursor
    }
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
