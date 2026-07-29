package com.hazbu.xpoint

import android.content.Context
import android.location.Location
import android.util.Log
import androidx.core.net.toUri
import com.hazbu.xpoint.Constants.AUTHORITY
import com.hazbu.xpoint.Constants.DEFAULT_ACCURACY
import com.hazbu.xpoint.Constants.DEFAULT_ALTITUDE
import com.hazbu.xpoint.Constants.DEFAULT_LAT
import com.hazbu.xpoint.Constants.DEFAULT_LONG
import com.hazbu.xpoint.Constants.DEFAULT_SPEED
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.util.Locale

class XPointModule : XposedModule() {

    private var fakeLat = DEFAULT_LAT
    private var fakeLong = DEFAULT_LONG
    private val tagX = "xPoint"
    private val modulePackage = "com.hazbu.xpoint"

    override fun onPackageReady(param: PackageReadyParam) {
        super.onPackageReady(param)

        Log.d(tagX, "onPackageReady: ${param.packageName}")

        if (param.packageName == modulePackage) return

        Log.d(tagX, "Hooking ${param.packageName} via libxposed")

        val classLoader = param.classLoader
        hookApplication(classLoader)
        hookLocation(classLoader)
        hookLocationManager(classLoader)
        hookGeocoder(classLoader)
    }

    private fun hookApplication(classLoader: ClassLoader) {
        try {
            val appClass = classLoader.loadClass("android.app.Application")
            val onCreateMethod = appClass.getDeclaredMethod("onCreate")
            hook(onCreateMethod).intercept { chain ->
                val result = chain.proceed()
                val context = chain.thisObject as Context
                refreshCoordinates(context)
                result
            }
        } catch (_: Exception) {
            Log.e(tagX, "Application hook failed")
        }
    }

    private fun refreshCoordinates(context: Context) {
        try {
            val uri = "content://$AUTHORITY".toUri()
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    fakeLat = cursor.getString(0).toDoubleOrNull() ?: DEFAULT_LAT
                    fakeLong = cursor.getString(1).toDoubleOrNull() ?: DEFAULT_LONG
                    Log.d(tagX, "Data refreshed: $fakeLat, $fakeLong")
                }
            }
        } catch (e: Exception) {
            Log.e(tagX, "Provider access failed: ${e.message}")
        }
    }

    private fun hookLocation(classLoader: ClassLoader) {
        try {
            val locationClass = classLoader.loadClass("android.location.Location")
            
            hook(locationClass.getDeclaredMethod("getLatitude")).intercept { _ -> fakeLat }
            hook(locationClass.getDeclaredMethod("getLongitude")).intercept { _ -> fakeLong }
            hook(locationClass.getDeclaredMethod("isFromMockProvider")).intercept { _ -> false }
            hook(locationClass.getDeclaredMethod("getAccuracy")).intercept { _ -> DEFAULT_ACCURACY }
            hook(locationClass.getDeclaredMethod("getSpeed")).intercept { _ -> DEFAULT_SPEED }
            hook(locationClass.getDeclaredMethod("getAltitude")).intercept { _ -> DEFAULT_ALTITUDE }
            
        } catch (e: Exception) {
            Log.e(tagX, "Location hook failed: ${e.message}")
        }
    }

    private fun hookLocationManager(classLoader: ClassLoader) {
        try {
            val lmClass = classLoader.loadClass("android.location.LocationManager")
            val lastKnownMethod = lmClass.getDeclaredMethod("getLastKnownLocation", String::class.java)
            
            hook(lastKnownMethod).intercept { chain ->
                val loc = chain.proceed() as? Location ?: Location("gps")
                loc.latitude = fakeLat
                loc.longitude = fakeLong
                loc.accuracy = DEFAULT_ACCURACY
                loc.time = System.currentTimeMillis()
                loc.elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
                loc
            }
        } catch (_: Exception) {}
    }

    private fun hookGeocoder(classLoader: ClassLoader) {
        try {
            val geocoderClass = classLoader.loadClass("android.location.Geocoder")
            hook(geocoderClass.getDeclaredMethod("isPresent")).intercept { _ -> true }

            val getFromLocMethod = geocoderClass.getDeclaredMethod(
                "getFromLocation",
                Double::class.javaPrimitiveType,
                Double::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )

            hook(getFromLocMethod).intercept { _ ->
                Log.d(tagX, "Geocoder intercepted for $fakeLat, $fakeLong")
                
                val addressClass = classLoader.loadClass("android.location.Address")
                val address = addressClass.getConstructor(Locale::class.java).newInstance(Locale.getDefault())
                
                addressClass.getDeclaredMethod("setAddressLine", Int::class.javaPrimitiveType, String::class.java).invoke(address, 0, String.format(Locale.US, "%.4f, %.4f", fakeLat, fakeLong))
                addressClass.getDeclaredMethod("setLocality", String::class.java).invoke(address, "Sidoarjo")
                addressClass.getDeclaredMethod("setAdminArea", String::class.java).invoke(address, "Jawa Timur")
                addressClass.getDeclaredMethod("setCountryName", String::class.java).invoke(address, "Indonesia")
                addressClass.getDeclaredMethod("setLatitude", Double::class.javaPrimitiveType).invoke(address, fakeLat)
                addressClass.getDeclaredMethod("setLongitude", Double::class.javaPrimitiveType).invoke(address, fakeLong)

                listOf(address)
            }
        } catch (_: Exception) {}
    }
}
