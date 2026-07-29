package com.hazbu.xpoint

import android.content.Context
import android.net.Uri
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.util.Locale

class XPointModule : IXposedHookLoadPackage {

    private var fakeLat = 0.0
    private var fakeLong = 0.0
    private var isInitialized = false

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName == "com.hazbu.xpoint") return

        XposedBridge.log("xPoint: Hooking ${lpparam.packageName}")

        // Hook ContextWrapper.attachBaseContext to get a Context and fetch data
        XposedHelpers.findAndHookMethod("android.content.ContextWrapper", lpparam.classLoader, "attachBaseContext", Context::class.java, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!isInitialized) {
                    val context = param.thisObject as Context
                    refreshCoordinates(context)
                    isInitialized = true
                }
            }
        })

        hookLocation(lpparam)
        hookGeocoder(lpparam)
    }

    private fun refreshCoordinates(context: Context) {
        try {
            val uri = Uri.parse("content://com.hazbu.xpoint.provider")
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    fakeLat = cursor.getString(0).toDoubleOrNull() ?: 0.0
                    fakeLong = cursor.getString(1).toDoubleOrNull() ?: 0.0
                    XposedBridge.log("xPoint: Data loaded from provider: $fakeLat, $fakeLong")
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
                if (fakeLat != 0.0) param.result = fakeLat
            }
        })

        XposedHelpers.findAndHookMethod(locationClass, lpparam.classLoader, "getLongitude", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (fakeLong != 0.0) param.result = fakeLong
            }
        })
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
                        XposedHelpers.callMethod(address, "setLocality", "Local Area")
                        XposedHelpers.callMethod(address, "setAdminArea", "Region")
                        XposedHelpers.callMethod(address, "setCountryName", "Location")
                        XposedHelpers.callMethod(address, "setLatitude", fakeLat)
                        XposedHelpers.callMethod(address, "setLongitude", fakeLong)

                        addresses.add(address)
                        param.result = addresses
                    }
                }
            )
        } catch (e: Exception) {}
    }
}
