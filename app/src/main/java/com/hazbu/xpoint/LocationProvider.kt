package com.hazbu.xpoint
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import com.hazbu.xpoint.Constants.DEFAULT_ACCURACY_MAX
import com.hazbu.xpoint.Constants.DEFAULT_ACCURACY_MIN
import com.hazbu.xpoint.Constants.DEFAULT_ALTITUDE
import com.hazbu.xpoint.Constants.DEFAULT_IS_ENABLED
import com.hazbu.xpoint.Constants.DEFAULT_JITTER_RADIUS
import com.hazbu.xpoint.Constants.DEFAULT_LAT
import com.hazbu.xpoint.Constants.DEFAULT_LONG
import com.hazbu.xpoint.Constants.DEFAULT_V_ACCURACY_MAX
import com.hazbu.xpoint.Constants.DEFAULT_V_ACCURACY_MIN
import com.hazbu.xpoint.Constants.KEY_ACCURACY_MAX
import com.hazbu.xpoint.Constants.KEY_ACCURACY_MIN
import com.hazbu.xpoint.Constants.KEY_ALTITUDE
import com.hazbu.xpoint.Constants.KEY_IS_ENABLED
import com.hazbu.xpoint.Constants.KEY_JITTER_RADIUS
import com.hazbu.xpoint.Constants.KEY_LAT
import com.hazbu.xpoint.Constants.KEY_LONG
import com.hazbu.xpoint.Constants.KEY_V_ACCURACY_MAX
import com.hazbu.xpoint.Constants.KEY_V_ACCURACY_MIN
import com.hazbu.xpoint.Constants.METHOD_GET_LOCATION
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
            KEY_V_ACCURACY_MIN, KEY_V_ACCURACY_MAX, KEY_IS_ENABLED, KEY_ALTITUDE
        ))
        cursor.addRow(arrayOf(
            prefs?.getString(KEY_LAT, DEFAULT_LAT.toString()) ?: DEFAULT_LAT.toString(),
            prefs?.getString(KEY_LONG, DEFAULT_LONG.toString()) ?: DEFAULT_LONG.toString(),
            prefs?.getFloat(KEY_ACCURACY_MIN, DEFAULT_ACCURACY_MIN)?.toString() ?: DEFAULT_ACCURACY_MIN.toString(),
            prefs?.getFloat(KEY_ACCURACY_MAX, DEFAULT_ACCURACY_MAX)?.toString() ?: DEFAULT_ACCURACY_MAX.toString(),
            prefs?.getFloat(KEY_JITTER_RADIUS, DEFAULT_JITTER_RADIUS)?.toString() ?: DEFAULT_JITTER_RADIUS.toString(),
            prefs?.getFloat(KEY_V_ACCURACY_MIN, DEFAULT_V_ACCURACY_MIN)?.toString() ?: DEFAULT_V_ACCURACY_MIN.toString(),
            prefs?.getFloat(KEY_V_ACCURACY_MAX, DEFAULT_V_ACCURACY_MAX)?.toString() ?: DEFAULT_V_ACCURACY_MAX.toString(),
            (prefs?.getBoolean(KEY_IS_ENABLED, DEFAULT_IS_ENABLED) ?: DEFAULT_IS_ENABLED).toString(),
            prefs?.getString(KEY_ALTITUDE, DEFAULT_ALTITUDE.toString()) ?: DEFAULT_ALTITUDE.toString()
        ))
        context?.contentResolver?.let { cr ->
            cursor.setNotificationUri(cr, uri)
        }
        return cursor
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method == METHOD_GET_LOCATION) {
            val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return Bundle().apply {
                putString(KEY_LAT, prefs?.getString(KEY_LAT, DEFAULT_LAT.toString()) ?: DEFAULT_LAT.toString())
                putString(KEY_LONG, prefs?.getString(KEY_LONG, DEFAULT_LONG.toString()) ?: DEFAULT_LONG.toString())
                putString(KEY_ALTITUDE, prefs?.getString(KEY_ALTITUDE, DEFAULT_ALTITUDE.toString()) ?: DEFAULT_ALTITUDE.toString())
                putFloat(KEY_ACCURACY_MIN, prefs?.getFloat(KEY_ACCURACY_MIN, DEFAULT_ACCURACY_MIN) ?: DEFAULT_ACCURACY_MIN)
                putFloat(KEY_ACCURACY_MAX, prefs?.getFloat(KEY_ACCURACY_MAX, DEFAULT_ACCURACY_MAX) ?: DEFAULT_ACCURACY_MAX)
                putFloat(KEY_JITTER_RADIUS, prefs?.getFloat(KEY_JITTER_RADIUS, DEFAULT_JITTER_RADIUS) ?: DEFAULT_JITTER_RADIUS)
                putFloat(KEY_V_ACCURACY_MIN, prefs?.getFloat(KEY_V_ACCURACY_MIN, DEFAULT_V_ACCURACY_MIN) ?: DEFAULT_V_ACCURACY_MIN)
                putFloat(KEY_V_ACCURACY_MAX, prefs?.getFloat(KEY_V_ACCURACY_MAX, DEFAULT_V_ACCURACY_MAX) ?: DEFAULT_V_ACCURACY_MAX)
                putBoolean(KEY_IS_ENABLED, prefs?.getBoolean(KEY_IS_ENABLED, DEFAULT_IS_ENABLED) ?: DEFAULT_IS_ENABLED)
            }
        }
        return super.call(method, arg, extras)
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
