package com.hazbu.xpoint

import android.annotation.SuppressLint
import android.content.Context
import android.database.ContentObserver
import android.location.Address
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.net.toUri
import com.hazbu.xpoint.Constants.AUTHORITY
import com.hazbu.xpoint.Constants.DEFAULT_ACCURACY_MAX
import com.hazbu.xpoint.Constants.DEFAULT_ACCURACY_MIN
import com.hazbu.xpoint.Constants.DEFAULT_ALTITUDE
import com.hazbu.xpoint.Constants.DEFAULT_IS_ENABLED
import com.hazbu.xpoint.Constants.DEFAULT_JITTER_RADIUS
import com.hazbu.xpoint.Constants.DEFAULT_LAT
import com.hazbu.xpoint.Constants.DEFAULT_LONG
import com.hazbu.xpoint.Constants.DEFAULT_SPEED
import com.hazbu.xpoint.Constants.DEFAULT_V_ACCURACY_MAX
import com.hazbu.xpoint.Constants.DEFAULT_V_ACCURACY_MIN
import com.hazbu.xpoint.Constants.JITTER_INTERVAL_MS
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
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class XPointModule : XposedModule() {
    @Volatile
    private var fakeLat = DEFAULT_LAT
    @Volatile
    private var fakeLong = DEFAULT_LONG
    @Volatile
    private var fakeAltitude = DEFAULT_ALTITUDE
    @Volatile
    private var accuracyMin = DEFAULT_ACCURACY_MIN
    @Volatile
    private var accuracyMax = DEFAULT_ACCURACY_MAX
    @Volatile
    private var vAccuracyMin = DEFAULT_V_ACCURACY_MIN
    @Volatile
    private var vAccuracyMax = DEFAULT_V_ACCURACY_MAX
    @Volatile
    private var jitterRadius = DEFAULT_JITTER_RADIUS
    @Volatile
    private var isEnabled = DEFAULT_IS_ENABLED

    @Volatile
    private var cachedJitterLat = DEFAULT_LAT
    @Volatile
    private var cachedJitterLong = DEFAULT_LONG
    @Volatile
    private var cachedAltitude = DEFAULT_ALTITUDE
    @Volatile
    private var cachedSpeed = DEFAULT_SPEED
    @Volatile
    private var cachedBearing = 0.0f
    @Volatile
    private var lastJitterUpdateTime = 0L

    private var observerRegistered = false

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
                val context = chain.thisObject as? Context
                if (context != null && !observerRegistered) {
                    refreshCoordinates(context)
                    registerLocationObserver(context)
                }
                result
            }
        } catch (e: Exception) {
            log(Log.WARN, "xPoint", "Failed to hook attachBaseContext: ${e.message}")
        }

        hookLocation(param)
        hookLocationManager(param)
        hookGeocoder(param)
        hookSettings(param)
    }

    private fun hookManagerApp(param: PackageReadyParam) {
        try {
            val clazz = param.classLoader.loadClass("com.hazbu.xpoint.MainActivity")
            hook(clazz.getDeclaredMethod("checkSelfActive")).intercept { true }
        } catch (e: Exception) {
            log(Log.ERROR, "xPoint", "Failed to hook manager app: ${e.message}")
        }
    }

    private fun registerLocationObserver(context: Context) {
        if (observerRegistered) return
        try {
            val uri = "content://$AUTHORITY".toUri()
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    Log.i("xPoint", "ContentObserver triggered: refreshing coordinates")
                    refreshCoordinates(context)
                }
            }
            context.contentResolver.registerContentObserver(uri, true, observer)
            observerRegistered = true
            Log.i("xPoint", "ContentObserver registered successfully for $uri")
        } catch (e: Exception) {
            Log.w("xPoint", "Failed to register ContentObserver: ${e.message}")
        }
    }

    private fun refreshCoordinates(context: Context) {
        try {
            val uri = "content://$AUTHORITY".toUri()

            val bundle = try {
                context.contentResolver.call(uri, METHOD_GET_LOCATION, null, null)
            } catch (_: Exception) {
                null
            }

            if (bundle != null) {
                fakeLat = bundle.getString(KEY_LAT, "$DEFAULT_LAT")?.toDoubleOrNull() ?: DEFAULT_LAT
                fakeLong = bundle.getString(KEY_LONG, "$DEFAULT_LONG")?.toDoubleOrNull() ?: DEFAULT_LONG
                fakeAltitude = bundle.getString(KEY_ALTITUDE, "$DEFAULT_ALTITUDE")?.toDoubleOrNull() ?: DEFAULT_ALTITUDE
                accuracyMin = bundle.getFloat(KEY_ACCURACY_MIN, DEFAULT_ACCURACY_MIN)
                accuracyMax = bundle.getFloat(KEY_ACCURACY_MAX, DEFAULT_ACCURACY_MAX)
                jitterRadius = bundle.getFloat(KEY_JITTER_RADIUS, DEFAULT_JITTER_RADIUS)
                vAccuracyMin = bundle.getFloat(KEY_V_ACCURACY_MIN, DEFAULT_V_ACCURACY_MIN)
                vAccuracyMax = bundle.getFloat(KEY_V_ACCURACY_MAX, DEFAULT_V_ACCURACY_MAX)
                isEnabled = bundle.getBoolean(KEY_IS_ENABLED, DEFAULT_IS_ENABLED)
                lastJitterUpdateTime = 0L
                Log.i("xPoint", "Data refreshed via call: $fakeLat, $fakeLong, alt: $fakeAltitude, enabled: $isEnabled, range: $accuracyMin-$accuracyMax, v-range: $vAccuracyMin-$vAccuracyMax, jitter: $jitterRadius")
                return
            }

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    fakeLat = cursor.getString(0).toDoubleOrNull() ?: DEFAULT_LAT
                    fakeLong = cursor.getString(1).toDoubleOrNull() ?: DEFAULT_LONG
                    accuracyMin = cursor.getString(2).toFloatOrNull() ?: DEFAULT_ACCURACY_MIN
                    accuracyMax = cursor.getString(3).toFloatOrNull() ?: DEFAULT_ACCURACY_MAX
                    jitterRadius = cursor.getString(4).toFloatOrNull() ?: DEFAULT_JITTER_RADIUS
                    vAccuracyMin = cursor.getString(5).toFloatOrNull() ?: DEFAULT_V_ACCURACY_MIN
                    vAccuracyMax = cursor.getString(6).toFloatOrNull() ?: DEFAULT_V_ACCURACY_MAX
                    if (cursor.columnCount > 7) {
                        isEnabled = cursor.getString(7).toBooleanStrictOrNull() ?: DEFAULT_IS_ENABLED
                    }
                    if (cursor.columnCount > 8) {
                        fakeAltitude = cursor.getString(8).toDoubleOrNull() ?: DEFAULT_ALTITUDE
                    }
                    lastJitterUpdateTime = 0L
                    Log.i("xPoint", "Data refreshed via query: $fakeLat, $fakeLong, alt: $fakeAltitude, enabled: $isEnabled, range: $accuracyMin-$accuracyMax, v-range: $vAccuracyMin-$vAccuracyMax, jitter: $jitterRadius")
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

    @Synchronized
    private fun updateJitterIfNeeded() {
        val now = SystemClock.elapsedRealtime()
        val timeDiffMs = now - lastJitterUpdateTime
        if (lastJitterUpdateTime != 0L && timeDiffMs < JITTER_INTERVAL_MS) {
            return
        }
        val isFirstUpdate = (lastJitterUpdateTime == 0L)
        val prevLat = cachedJitterLat
        val prevLong = cachedJitterLong
        lastJitterUpdateTime = now

        if (jitterRadius <= 0f) {
            cachedJitterLat = fakeLat
            cachedJitterLong = fakeLong
            cachedSpeed = 0.0f
            cachedAltitude = fakeAltitude
            return
        }

        val angle = Random.nextDouble() * 2 * Math.PI
        val distance = sqrt(Random.nextDouble()) * jitterRadius.toDouble()
        val latOffset = (distance * cos(angle)) / 111111.0
        val newLat = fakeLat + latOffset
        val lonOffset = (distance * sin(angle)) / (111111.0 * cos(Math.toRadians(newLat)))
        val newLon = fakeLong + lonOffset

        cachedJitterLat = newLat
        cachedJitterLong = newLon

        val vDriftRange = (vAccuracyMax.coerceAtLeast(1.0f) * 0.15f).coerceIn(0.2f, 1.5f)
        val vOffset = (Random.nextDouble() * 2 - 1) * vDriftRange
        cachedAltitude = fakeAltitude + vOffset

        if (!isFirstUpdate && timeDiffMs > 0L) {
            try {
                val results = FloatArray(2)
                Location.distanceBetween(prevLat, prevLong, newLat, newLon, results)
                val movedDistMeters = results[0]
                val timeDiffSeconds = timeDiffMs / 1000.0f
                cachedSpeed = if (timeDiffSeconds > 0f) movedDistMeters / timeDiffSeconds else 0.0f
                cachedBearing = (results[1] + 360f) % 360f
            } catch (_: Exception) {
                cachedSpeed = DEFAULT_SPEED
            }
        } else {
            cachedSpeed = 0.0f
        }
    }

    private fun getJitterLat(): Double {
        updateJitterIfNeeded()
        return cachedJitterLat
    }

    private fun getJitterLong(): Double {
        updateJitterIfNeeded()
        return cachedJitterLong
    }

    private fun getJitterAltitude(): Double {
        updateJitterIfNeeded()
        return cachedAltitude
    }

    private fun getJitterSpeed(): Float {
        updateJitterIfNeeded()
        return cachedSpeed
    }

    private fun getJitterBearing(): Float {
        updateJitterIfNeeded()
        return cachedBearing
    }

    private fun fillLocationData(loc: Location): Location {
        loc.latitude = getJitterLat()
        loc.longitude = getJitterLong()
        loc.altitude = getJitterAltitude()
        loc.accuracy = getSpoofedAccuracy()
        loc.speed = getJitterSpeed()
        loc.bearing = getJitterBearing()
        loc.time = System.currentTimeMillis()
        loc.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        val extras = loc.extras?.let { Bundle(it) } ?: Bundle()
        extras.remove("mockLocation")
        if (!extras.containsKey("satellites")) {
            extras.putInt("satellites", 12)
        }
        loc.extras = extras
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                loc.verticalAccuracyMeters = getSpoofedVerticalAccuracy()
            } catch (_: Exception) {}
        }
        return loc
    }

    private fun createFakeLocation(provider: String = "gps"): Location {
        return fillLocationData(Location(provider))
    }

    private fun hookLocation(param: PackageReadyParam) {
        try {
            val locationClass = param.classLoader.loadClass("android.location.Location")

            hook(locationClass.getDeclaredMethod("getLatitude")).intercept { chain ->
                if (!isEnabled) chain.proceed() else getJitterLat()
            }
            hook(locationClass.getDeclaredMethod("getLongitude")).intercept { chain ->
                if (!isEnabled) chain.proceed() else getJitterLong()
            }
            hook(locationClass.getDeclaredMethod("isFromMockProvider")).intercept { chain ->
                if (!isEnabled) chain.proceed() else false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    hook(locationClass.getDeclaredMethod("isMock")).intercept { chain ->
                        if (!isEnabled) chain.proceed() else false
                    }
                } catch (_: Exception) {}
            }

            hook(locationClass.getDeclaredMethod("getAccuracy")).intercept { chain ->
                if (!isEnabled) chain.proceed() else getSpoofedAccuracy()
            }

            try {
                hook(locationClass.getDeclaredMethod("getVerticalAccuracyMeters")).intercept { chain ->
                    if (!isEnabled) chain.proceed() else getSpoofedVerticalAccuracy()
                }
                hook(locationClass.getDeclaredMethod("hasVerticalAccuracy")).intercept { chain ->
                    if (!isEnabled) chain.proceed() else true
                }
            } catch (_: Exception) {}

            hook(locationClass.getDeclaredMethod("getSpeed")).intercept { chain ->
                if (!isEnabled) chain.proceed() else getJitterSpeed()
            }
            try {
                hook(locationClass.getDeclaredMethod("hasSpeed")).intercept { chain ->
                    if (!isEnabled) chain.proceed() else true
                }
            } catch (_: Exception) {}

            try {
                hook(locationClass.getDeclaredMethod("getBearing")).intercept { chain ->
                    if (!isEnabled) chain.proceed() else getJitterBearing()
                }
                hook(locationClass.getDeclaredMethod("hasBearing")).intercept { chain ->
                    if (!isEnabled) chain.proceed() else true
                }
            } catch (_: Exception) {}

            hook(locationClass.getDeclaredMethod("getAltitude")).intercept { chain ->
                if (!isEnabled) chain.proceed() else getJitterAltitude()
            }
            try {
                hook(locationClass.getDeclaredMethod("hasAltitude")).intercept { chain ->
                    if (!isEnabled) chain.proceed() else true
                }
            } catch (_: Exception) {}

            try {
                hook(locationClass.getDeclaredMethod("getExtras")).intercept { chain ->
                    val original = chain.proceed() as? Bundle
                    if (!isEnabled) return@intercept original
                    val bundle = if (original != null) Bundle(original) else Bundle()
                    bundle.remove("mockLocation")
                    if (!bundle.containsKey("satellites")) {
                        bundle.putInt("satellites", 12)
                    }
                    bundle
                }
            } catch (e: Exception) {
                Log.w("xPoint", "Failed to hook getExtras: ${e.message}")
            }

            try {
                hook(locationClass.getDeclaredMethod("getTime")).intercept { chain ->
                    val original = chain.proceed() as Long
                    if (!isEnabled) {
                        original
                    } else {
                        val now = System.currentTimeMillis()
                        if (original == 0L || Math.abs(now - original) > 5000L) now else original
                    }
                }
            } catch (e: Exception) {
                Log.w("xPoint", "Failed to hook getTime: ${e.message}")
            }

            try {
                hook(locationClass.getDeclaredMethod("getElapsedRealtimeNanos")).intercept { chain ->
                    val original = chain.proceed() as Long
                    if (!isEnabled) {
                        original
                    } else {
                        val now = SystemClock.elapsedRealtimeNanos()
                        if (original == 0L || Math.abs(now - original) > 5_000_000_000L) now else original
                    }
                }
            } catch (e: Exception) {
                Log.w("xPoint", "Failed to hook getElapsedRealtimeNanos: ${e.message}")
            }
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
                    if (!isEnabled) return@intercept result
                    val provider = chain.args[0] as? String ?: "gps"
                    val loc = result as? Location ?: Location(provider)
                    fillLocationData(loc)
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
                        if (!isEnabled) return@intercept result
                        val provider = chain.args[0] as? String ?: "gps"
                        val loc = result as? Location ?: Location(provider)
                        fillLocationData(loc)
                    }
                } catch (e: Exception) {
                    Log.w("xPoint", "Failed to hook getLastKnownLocation(String, LastLocationRequest): ${e.message}")
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val m = lmClass.getDeclaredMethod(
                        "getCurrentLocation",
                        String::class.java,
                        android.os.CancellationSignal::class.java,
                        java.util.concurrent.Executor::class.java,
                        java.util.function.Consumer::class.java
                    )
                    hook(m).intercept { chain ->
                        if (!isEnabled) return@intercept chain.proceed()
                        val provider = chain.args[0] as? String ?: "gps"
                        val cancelSignal = chain.args[1] as? android.os.CancellationSignal
                        val executor = chain.args[2] as? java.util.concurrent.Executor
                        @Suppress("UNCHECKED_CAST")
                        val consumer = chain.args[3] as? java.util.function.Consumer<Location>

                        if (cancelSignal?.isCanceled == true) return@intercept null
                        val fakeLoc = createFakeLocation(provider)
                        executor?.execute {
                            consumer?.accept(fakeLoc)
                        }
                        null
                    }
                } catch (e: Exception) {
                    Log.w("xPoint", "Failed to hook getCurrentLocation (API 30): ${e.message}")
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    val locRequestClass = param.classLoader.loadClass("android.location.LocationRequest")
                    val m = lmClass.getDeclaredMethod(
                        "getCurrentLocation",
                        String::class.java,
                        locRequestClass,
                        android.os.CancellationSignal::class.java,
                        java.util.concurrent.Executor::class.java,
                        java.util.function.Consumer::class.java
                    )
                    hook(m).intercept { chain ->
                        if (!isEnabled) return@intercept chain.proceed()
                        val provider = chain.args[0] as? String ?: "gps"
                        val cancelSignal = chain.args[2] as? android.os.CancellationSignal
                        val executor = chain.args[3] as? java.util.concurrent.Executor
                        @Suppress("UNCHECKED_CAST")
                        val consumer = chain.args[4] as? java.util.function.Consumer<Location>

                        if (cancelSignal?.isCanceled == true) return@intercept null
                        val fakeLoc = createFakeLocation(provider)
                        executor?.execute {
                            consumer?.accept(fakeLoc)
                        }
                        null
                    }
                } catch (e: Exception) {
                    Log.w("xPoint", "Failed to hook getCurrentLocation (API 31): ${e.message}")
                }
            }

            try {
                val m = lmClass.getDeclaredMethod("isProviderEnabled", String::class.java)
                hook(m).intercept { chain ->
                    if (!isEnabled) return@intercept chain.proceed()
                    val provider = chain.args[0] as? String
                    if (provider == "gps" || provider == "network" || provider == "fused") {
                        true
                    } else {
                        chain.proceed()
                    }
                }
            } catch (e: Exception) {
                Log.w("xPoint", "Failed to hook isProviderEnabled: ${e.message}")
            }

            try {
                val m = lmClass.getDeclaredMethod("getProviders", Boolean::class.javaPrimitiveType)
                hook(m).intercept { chain ->
                    @Suppress("UNCHECKED_CAST")
                    val result = chain.proceed() as? List<String>
                    if (!isEnabled) return@intercept result
                    val list = result?.toMutableList() ?: mutableListOf()
                    if (!list.contains("gps")) list.add("gps")
                    if (!list.contains("network")) list.add("network")
                    list
                }
            } catch (_: Exception) {}

            try {
                val criteriaClass = param.classLoader.loadClass("android.location.Criteria")
                val m = lmClass.getDeclaredMethod("getBestProvider", criteriaClass, Boolean::class.javaPrimitiveType)
                hook(m).intercept { chain ->
                    if (!isEnabled) chain.proceed() else "gps"
                }
            } catch (_: Exception) {}
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
                    if (!isEnabled) return@intercept chain.proceed()
                    val lat = getJitterLat()
                    val lon = getJitterLong()
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

    private fun hookSettings(param: PackageReadyParam) {
        try {
            val secureClass = param.classLoader.loadClass("android.provider.Settings\$Secure")
            val contentResolverClass = param.classLoader.loadClass("android.content.ContentResolver")

            try {
                val getStringMethod = secureClass.getDeclaredMethod("getString", contentResolverClass, String::class.java)
                hook(getStringMethod).intercept { chain ->
                    val name = chain.args[1] as? String
                    if (isEnabled && (name == "mock_location" || name == "allow_mock_location")) {
                        "0"
                    } else {
                        chain.proceed()
                    }
                }
            } catch (e: Exception) {
                Log.w("xPoint", "Failed to hook Settings.Secure.getString: ${e.message}")
            }

            try {
                val getInt3Method = secureClass.getDeclaredMethod("getInt", contentResolverClass, String::class.java, Int::class.javaPrimitiveType)
                hook(getInt3Method).intercept { chain ->
                    val name = chain.args[1] as? String
                    if (isEnabled && (name == "mock_location" || name == "allow_mock_location")) {
                        0
                    } else {
                        chain.proceed()
                    }
                }
            } catch (e: Exception) {
                Log.w("xPoint", "Failed to hook Settings.Secure.getInt(3 args): ${e.message}")
            }

            try {
                val getInt2Method = secureClass.getDeclaredMethod("getInt", contentResolverClass, String::class.java)
                hook(getInt2Method).intercept { chain ->
                    val name = chain.args[1] as? String
                    if (isEnabled && (name == "mock_location" || name == "allow_mock_location")) {
                        0
                    } else {
                        chain.proceed()
                    }
                }
            } catch (e: Exception) {
                Log.w("xPoint", "Failed to hook Settings.Secure.getInt(2 args): ${e.message}")
            }
        } catch (e: Exception) {
            Log.w("xPoint", "Failed to hook Settings: ${e.message}")
        }
    }
}
