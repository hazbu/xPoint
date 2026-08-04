package com.hazbu.xpoint

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Location
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.net.toUri
import com.hazbu.xpoint.Constants.AUTHORITY
import com.hazbu.xpoint.Constants.DEFAULT_ACCURACY
import com.hazbu.xpoint.Constants.DEFAULT_ALTITUDE
import com.hazbu.xpoint.Constants.DEFAULT_LAT
import com.hazbu.xpoint.Constants.DEFAULT_LONG
import com.hazbu.xpoint.Constants.DEFAULT_SPEED
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.util.Locale

class XPointModule : XposedModule {
    private var fakeLat = DEFAULT_LAT
    private var fakeLong = DEFAULT_LONG
    private var isInitialized = false

    constructor() : super()

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        super.onModuleLoaded(param)
        Log.i("xPoint", "Module loaded: ${param.processName}")
    }

    override fun onPackageReady(param: PackageReadyParam) {
        super.onPackageReady(param)
        Log.i("xPoint", "Package ready: ${param.packageName}")
        if (param.packageName == "com.hazbu.xpoint") {
            hookManagerApp(param)
            return
        }

        try {
            val contextWrapperClass = param.classLoader.loadClass("android.content.ContextWrapper")
            val attachBaseContextMethod = contextWrapperClass.getDeclaredMethod("attachBaseContext", Context::class.java)
            hook(attachBaseContextMethod).intercept { chain ->
                val result = chain.proceed()
                if (!isInitialized) {
                    val context = chain.thisObject as Context
                    refreshCoordinates(context)
                    isInitialized = true
                }
                result
            }
        } catch (e: Exception) {
            Log.e("xPoint", "Failed to hook attachBaseContext: ${e.message}")
        }

        hookLocation(param)
        hookLocationManager(param)
        hookGeocoder(param)
    }

    private fun hookManagerApp(param: PackageReadyParam) {
        try {
            val clazz = param.classLoader.loadClass("com.hazbu.xpoint.MainActivity")
            hook(clazz.getDeclaredMethod("checkSelfActive")).intercept { true }
        } catch (e: Exception) {
            Log.e("xPoint", "Failed to hook manager app: ${e.message}")
        }
    }

    private fun refreshCoordinates(context: Context) {
        try {
            val uri = "content://$AUTHORITY".toUri()
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    fakeLat = cursor.getString(0).toDoubleOrNull() ?: DEFAULT_LAT
                    fakeLong = cursor.getString(1).toDoubleOrNull() ?: DEFAULT_LONG
                    Log.i("xPoint", "Data refreshed: $fakeLat, $fakeLong")
                }
            }
        } catch (e: Exception) {
            Log.e("xPoint", "Provider access failed: ${e.message}")
        }
    }

    private fun hookLocation(param: PackageReadyParam) {
        try {
            val locationClass = param.classLoader.loadClass("android.location.Location")
            
            hook(locationClass.getDeclaredMethod("getLatitude")).intercept { fakeLat }
            hook(locationClass.getDeclaredMethod("getLongitude")).intercept { fakeLong }
            hook(locationClass.getDeclaredMethod("isFromMockProvider")).intercept { false }
            hook(locationClass.getDeclaredMethod("getAccuracy")).intercept { DEFAULT_ACCURACY }
            hook(locationClass.getDeclaredMethod("getSpeed")).intercept { DEFAULT_SPEED }
            hook(locationClass.getDeclaredMethod("getAltitude")).intercept { DEFAULT_ALTITUDE }
        } catch (e: Exception) {
            Log.e("xPoint", "Failed to hook Location: ${e.message}")
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun hookLocationManager(param: PackageReadyParam) {
        try {
            val lmClass = param.classLoader.loadClass("android.location.LocationManager")
            
            try {
                val m1 = lmClass.getDeclaredMethod("getLastKnownLocation", String::class.java)
                hook(m1).intercept { chain ->
                    val result = chain.proceed()
                    val loc = result as? Location ?: Location("gps")
                    loc.latitude = fakeLat
                    loc.longitude = fakeLong
                    loc.accuracy = DEFAULT_ACCURACY
                    loc.time = System.currentTimeMillis()
                    loc.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                    loc
                }
            } catch (e: Exception) {
                Log.w("xPoint", "Failed to hook getLastKnownLocation(String): ${e.message}")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    val lastLocationRequestClass = param.classLoader.loadClass("android.location.LastLocationRequest")
                    val m2 = lmClass.getDeclaredMethod("getLastKnownLocation", String::class.java, lastLocationRequestClass)
                    hook(m2).intercept { chain ->
                        val result = chain.proceed()
                        val loc = result as? Location ?: Location("gps")
                        loc.latitude = fakeLat
                        loc.longitude = fakeLong
                        loc.accuracy = DEFAULT_ACCURACY
                        loc.time = System.currentTimeMillis()
                        loc.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                        loc
                    }
                } catch (e: Exception) {
                    Log.w("xPoint", "Failed to hook getLastKnownLocation(String, LastLocationRequest): ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("xPoint", "Failed to hook LocationManager: ${e.message}")
        }
    }

    private fun hookGeocoder(param: PackageReadyParam) {
        try {
            val geocoderClass = param.classLoader.loadClass("android.location.Geocoder")
            
            try {
                hook(geocoderClass.getDeclaredMethod("isPresent")).intercept { true }
            } catch (_: NoSuchMethodException) {}

            try {
                val getFromLocation = geocoderClass.getDeclaredMethod(
                    "getFromLocation",
                    Double::class.javaPrimitiveType,
                    Double::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                hook(getFromLocation).intercept { chain ->
                    Log.i("xPoint", "Geocoder intercepted for $fakeLat, $fakeLong")
                    val addresses = ArrayList<Address>()
                    val address = Address(Locale.getDefault())
                    address.latitude = fakeLat
                    address.longitude = fakeLong
                    addresses.add(address)
                    addresses
                }
            } catch (e: Exception) {
                Log.w("xPoint", "Failed to hook getFromLocation: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e("xPoint", "Failed to hook Geocoder: ${e.message}")
        }
    }
}
