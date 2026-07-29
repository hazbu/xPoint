package com.hazbu.xpoint
import android.content.Context
import android.location.Location
import android.os.Build
import androidx.core.net.toUri
import com.hazbu.xpoint.Constants.AUTHORITY
import com.hazbu.xpoint.Constants.DEFAULT_ACCURACY
import com.hazbu.xpoint.Constants.DEFAULT_ALTITUDE
import com.hazbu.xpoint.Constants.DEFAULT_LAT
import com.hazbu.xpoint.Constants.DEFAULT_LONG
import com.hazbu.xpoint.Constants.DEFAULT_SPEED
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.util.Locale
class XPointModule : IXposedHookLoadPackage {
    private var fakeLat = DEFAULT_LAT
    private var fakeLong = DEFAULT_LONG
    private var isInitialized = false
    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName == "com.hazbu.xpoint") return
        XposedBridge.log("xPoint: Hooking ${lpparam.packageName}")
        XposedHelpers.findAndHookMethod(
            "android.content.ContextWrapper", 
            lpparam.classLoader, 
            "attachBaseContext", 
            Context::class.java, 
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!isInitialized) {
                        val context = param.thisObject as Context
                        refreshCoordinates(context)
                        isInitialized = true
                    }
                }
            }
        )
        hookLocation(lpparam)
        hookLocationManager(lpparam)
        hookGeocoder(lpparam)
    }
    private fun refreshCoordinates(context: Context) {
        try {
            val uri = "content://$AUTHORITY".toUri()
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    fakeLat = cursor.getString(0).toDoubleOrNull() ?: DEFAULT_LAT
                    fakeLong = cursor.getString(1).toDoubleOrNull() ?: DEFAULT_LONG
                    XposedBridge.log("xPoint: Data refreshed: $fakeLat, $fakeLong")
                }
            }
        } catch (e: Exception) {
            XposedBridge.log("xPoint: Provider access failed: ${e.message}")
        }
    }
    private fun hookLocation(lpparam: LoadPackageParam) {
        val locationClass = "android.location.Location"
        XposedHelpers.findAndHookMethod(locationClass, lpparam.classLoader, "getLatitude", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                param.result = fakeLat
            }
        })
        XposedHelpers.findAndHookMethod(locationClass, lpparam.classLoader, "getLongitude", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                param.result = fakeLong
            }
        })
        XposedHelpers.findAndHookMethod(locationClass, lpparam.classLoader, "isFromMockProvider", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                param.result = false
            }
        })
        XposedHelpers.findAndHookMethod(locationClass, lpparam.classLoader, "getAccuracy", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                param.result = DEFAULT_ACCURACY
            }
        })
        XposedHelpers.findAndHookMethod(locationClass, lpparam.classLoader, "getSpeed", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                param.result = DEFAULT_SPEED
            }
        })
        XposedHelpers.findAndHookMethod(locationClass, lpparam.classLoader, "getAltitude", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                param.result = DEFAULT_ALTITUDE
            }
        })
    }
    private fun hookLocationManager(lpparam: LoadPackageParam) {
        val lmClass = "android.location.LocationManager"
        val lastKnownHook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val loc = param.result as? Location ?: Location("gps")
                loc.latitude = fakeLat
                loc.longitude = fakeLong
                loc.accuracy = DEFAULT_ACCURACY
                loc.time = System.currentTimeMillis()
                loc.elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
                param.result = loc
            }
        }
        try {
            XposedHelpers.findAndHookMethod(lmClass, lpparam.classLoader, "getLastKnownLocation", String::class.java, lastKnownHook)
        } catch (_: Throwable) {}
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                XposedHelpers.findAndHookMethod(lmClass, lpparam.classLoader, "getLastKnownLocation", String::class.java, "android.location.LastLocationRequest", lastKnownHook)
            } catch (_: Throwable) {}
        }
    }
    private fun hookGeocoder(lpparam: LoadPackageParam) {
        val geocoderClass = "android.location.Geocoder"
        XposedHelpers.findAndHookMethod(geocoderClass, lpparam.classLoader, "isPresent", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.result = true
            }
        })
        try {
            XposedHelpers.findAndHookMethod(
                geocoderClass,
                lpparam.classLoader,
                "getFromLocation",
                Double::class.javaPrimitiveType,
                Double::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        XposedBridge.log("xPoint: Geocoder intercepted for $fakeLat, $fakeLong")
                        val addresses = ArrayList<Any?>()
                        val addressClass = XposedHelpers.findClass("android.location.Address", lpparam.classLoader)
                        val address = XposedHelpers.newInstance(addressClass, Locale.getDefault())
                        XposedHelpers.callMethod(address, "setAddressLine", 0, String.format(Locale.US, "%.4f, %.4f", fakeLat, fakeLong))
                        XposedHelpers.callMethod(address, "setLocality", "Sidoarjo")
                        XposedHelpers.callMethod(address, "setAdminArea", "Jawa Timur")
                        XposedHelpers.callMethod(address, "setCountryName", "Indonesia")
                        XposedHelpers.callMethod(address, "setLatitude", fakeLat)
                        XposedHelpers.callMethod(address, "setLongitude", fakeLong)
                        addresses.add(address)
                        param.result = addresses
                    }
                }
            )
        } catch (_: Exception) {}
    }
}

