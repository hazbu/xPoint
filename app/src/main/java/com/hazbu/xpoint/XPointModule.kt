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
import com.hazbu.xpoint.Constants.DEFAULT_ALTITUDE
import com.hazbu.xpoint.Constants.DEFAULT_ACCURACY_MAX
import com.hazbu.xpoint.Constants.DEFAULT_ACCURACY_MIN
import com.hazbu.xpoint.Constants.DEFAULT_V_ACCURACY_MAX
import com.hazbu.xpoint.Constants.DEFAULT_V_ACCURACY_MIN
import com.hazbu.xpoint.Constants.DEFAULT_JITTER_RADIUS
import com.hazbu.xpoint.Constants.DEFAULT_LAT
import com.hazbu.xpoint.Constants.DEFAULT_LONG
import com.hazbu.xpoint.Constants.DEFAULT_SPEED
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.util.Locale
import kotlin.math.cos
import kotlin.random.Random

class XPointModule : XposedModule() {
    private var fakeLat = DEFAULT_LAT
    private var fakeLong = DEFAULT_LONG
    private var accuracyMin = DEFAULT_ACCURACY_MIN
    private var accuracyMax = DEFAULT_ACCURACY_MAX
    private var vAccuracyMin = DEFAULT_V_ACCURACY_MIN
    private var vAccuracyMax = DEFAULT_V_ACCURACY_MAX
    private var jitterRadius = DEFAULT_JITTER_RADIUS
    private var isInitialized = false

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
            log(Log.WARN, "xPoint", "Failed to hook attachBaseContext: ${e.message}")
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
            log(Log.ERROR, "xPoint", "Failed to hook manager app: ${e.message}")
        }
    }

    private fun refreshCoordinates(context: Context) {
        try {
            val uri = "content://$AUTHORITY".toUri()
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    fakeLat = cursor.getString(0).toDoubleOrNull() ?: DEFAULT_LAT
                    fakeLong = cursor.getString(1).toDoubleOrNull() ?: DEFAULT_LONG
                    accuracyMin = cursor.getString(2).toFloatOrNull() ?: DEFAULT_ACCURACY_MIN
                    accuracyMax = cursor.getString(3).toFloatOrNull() ?: DEFAULT_ACCURACY_MAX
                    jitterRadius = cursor.getString(4).toFloatOrNull() ?: DEFAULT_JITTER_RADIUS
                    vAccuracyMin = cursor.getString(5).toFloatOrNull() ?: DEFAULT_V_ACCURACY_MIN
                    vAccuracyMax = cursor.getString(6).toFloatOrNull() ?: DEFAULT_V_ACCURACY_MAX
                    Log.i("xPoint", "Data refreshed: $fakeLat, $fakeLong, range: $accuracyMin-$accuracyMax, v-range: $vAccuracyMin-$vAccuracyMax, jitter: $jitterRadius")
                }
            }
        } catch (e: Exception) {
            Log.e("xPoint", "Provider access failed: ${e.message}")
        }
    }

    private fun getSpoofedAccuracy(): Float {
        return if (accuracyMin == accuracyMax) {
            accuracyMin
        } else {
            Random.nextFloat() * (accuracyMax - accuracyMin) + accuracyMin
        }
    }

    private fun getSpoofedVerticalAccuracy(): Float {
        return if (vAccuracyMin == vAccuracyMax) {
            vAccuracyMin
        } else {
            Random.nextFloat() * (vAccuracyMax - vAccuracyMin) + vAccuracyMin
        }
    }

    private fun getJitterLat(): Double {
        if (jitterRadius <= 0) return fakeLat
        val latOffset = (Random.nextDouble() * 2 - 1) * (jitterRadius / 111111.0)
        return fakeLat + latOffset
    }

    private fun getJitterLong(lat: Double): Double {
        if (jitterRadius <= 0) return fakeLong
        val lonOffset = (Random.nextDouble() * 2 - 1) * (jitterRadius / (111111.0 * cos(Math.toRadians(lat))))
        return fakeLong + lonOffset
    }

    private fun hookLocation(param: PackageReadyParam) {
        try {
            val locationClass = param.classLoader.loadClass("android.location.Location")
            
            hook(locationClass.getDeclaredMethod("getLatitude")).intercept { getJitterLat() }
            hook(locationClass.getDeclaredMethod("getLongitude")).intercept { getJitterLong(fakeLat) }
            hook(locationClass.getDeclaredMethod("isFromMockProvider")).intercept { false }
            hook(locationClass.getDeclaredMethod("getAccuracy")).intercept { getSpoofedAccuracy() }
            
            try {
                hook(locationClass.getDeclaredMethod("getVerticalAccuracyMeters")).intercept { getSpoofedVerticalAccuracy() }
                hook(locationClass.getDeclaredMethod("hasVerticalAccuracy")).intercept { true }
            } catch (_: Exception) {}

            hook(locationClass.getDeclaredMethod("getSpeed")).intercept { DEFAULT_SPEED }
            hook(locationClass.getDeclaredMethod("getAltitude")).intercept { DEFAULT_ALTITUDE }
        } catch (e: Exception) {
            Log.e("xPoint", "Failed to hook Location: ${e.message}")
        }
    }

    @SuppressLint("DiscouragedPrivateApi", "PrivateApi")
    private fun hookLocationManager(param: PackageReadyParam) {
        try {
            val lmClass = param.classLoader.loadClass("android.location.LocationManager")
            
            try {
                val m1 = lmClass.getDeclaredMethod("getLastKnownLocation", String::class.java)
                hook(m1).intercept { chain ->
                    val result = chain.proceed()
                    val loc = result as? Location ?: Location("gps")
                    val lat = getJitterLat()
                    loc.latitude = lat
                    loc.longitude = getJitterLong(lat)
                    loc.accuracy = getSpoofedAccuracy()
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
                        val lat = getJitterLat()
                        loc.latitude = lat
                        loc.longitude = getJitterLong(lat)
                        loc.accuracy = getSpoofedAccuracy()
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
                hook(getFromLocation).intercept { _ ->
                    val lat = getJitterLat()
                    val lon = getJitterLong(lat)
                    Log.i("xPoint", "Geocoder intercepted for $lat, $lon")
                    val addresses = ArrayList<Address>()
                    val address = Address(Locale.getDefault())
                    address.latitude = lat
                    address.longitude = lon
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
