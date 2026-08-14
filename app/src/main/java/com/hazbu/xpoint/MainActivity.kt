package com.hazbu.xpoint

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.slider.RangeSlider
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
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
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlin.math.cos
import kotlin.random.Random

class MainActivity : AppCompatActivity(), XposedServiceHelper.OnServiceListener {

    private lateinit var tvModuleStatus: TextView
    private lateinit var cardModuleStatus: com.google.android.material.card.MaterialCardView
    private lateinit var cardScopedApps: com.google.android.material.card.MaterialCardView
    private lateinit var layoutScopedApps: LinearLayout
    
    private lateinit var sliderAccuracyRange: RangeSlider
    private lateinit var tvAccuracyRangeVal: TextView

    private lateinit var sliderVAccuracyRange: RangeSlider
    private lateinit var tvVAccuracyRangeVal: TextView

    private lateinit var sliderJitterRadius: Slider
    private lateinit var tvJitterRadiusVal: TextView
    private lateinit var tvPreviewCoords: TextView
    private lateinit var etLatitude: TextInputEditText
    private lateinit var etLongitude: TextInputEditText
    
    private var mXposedService: XposedService? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val updatePreviewRunnable = object : Runnable {
        override fun run() {
            updatePreview()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        XposedServiceHelper.registerListener(this)
        setupWindowInsets()
        setupUI()
        updateModuleStatusUI()
    }

    override fun onResume() {
        super.onResume()
        updateModuleStatusUI()
        handler.post(updatePreviewRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updatePreviewRunnable)
    }

    override fun onServiceBind(service: XposedService) {
        Log.i("xPoint", "Xposed service bound. Scope size: ${service.scope.size}")
        mXposedService = service
        runOnUiThread { updateModuleStatusUI() }
    }

    override fun onServiceDied(service: XposedService) {
        mXposedService = null
        runOnUiThread { updateModuleStatusUI() }
    }

    private fun setupWindowInsets() {
        val mainView = findViewById<View>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(mainView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val density = resources.displayMetrics.density
            val basePadding = (24 * density).toInt()
            v.setPadding(
                systemBars.left + basePadding,
                systemBars.top + basePadding,
                systemBars.right + basePadding,
                systemBars.bottom + basePadding
            )
            insets
        }
    }

    private fun setupUI() {
        etLatitude = findViewById(R.id.et_latitude)
        etLongitude = findViewById(R.id.et_longitude)
        val btnSave = findViewById<android.widget.Button>(R.id.btn_save)
        val tvTitle = findViewById<TextView>(R.id.tv_title)
        
        tvModuleStatus = findViewById(R.id.tv_module_status)
        cardModuleStatus = findViewById(R.id.card_module_status)
        cardScopedApps = findViewById(R.id.card_scoped_apps)
        layoutScopedApps = findViewById(R.id.layout_scoped_apps)
        
        sliderAccuracyRange = findViewById(R.id.slider_accuracy_range)
        tvAccuracyRangeVal = findViewById(R.id.tv_accuracy_range_val)
        val btnResetAccuracy = findViewById<View>(R.id.btn_reset_accuracy)

        sliderVAccuracyRange = findViewById(R.id.slider_v_accuracy_range)
        tvVAccuracyRangeVal = findViewById(R.id.tv_v_accuracy_range_val)
        val btnResetVAccuracy = findViewById<View>(R.id.btn_reset_v_accuracy)

        sliderJitterRadius = findViewById(R.id.slider_jitter_radius)
        tvJitterRadiusVal = findViewById(R.id.tv_jitter_radius_val)
        val btnResetJitter = findViewById<View>(R.id.btn_reset_jitter)
        tvPreviewCoords = findViewById(R.id.tv_preview_coords)

        setupTitleSpannable(tvTitle)
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        
        etLatitude.setText(prefs.getString(KEY_LAT, DEFAULT_LAT.toString()))
        etLongitude.setText(prefs.getString(KEY_LONG, DEFAULT_LONG.toString()))
        
        // Horizontal Accuracy
        val minAcc = prefs.getFloat(KEY_ACCURACY_MIN, DEFAULT_ACCURACY_MIN)
        val maxAcc = prefs.getFloat(KEY_ACCURACY_MAX, DEFAULT_ACCURACY_MAX)
        sliderAccuracyRange.setValues(minAcc, maxAcc)
        updateAccuracyLabel(minAcc, maxAcc)
        sliderAccuracyRange.addOnChangeListener { slider, _, _ ->
            updateAccuracyLabel(slider.values[0], slider.values[1])
        }
        btnResetAccuracy.setOnClickListener {
            sliderAccuracyRange.setValues(DEFAULT_ACCURACY_MIN, DEFAULT_ACCURACY_MAX)
            updateAccuracyLabel(DEFAULT_ACCURACY_MIN, DEFAULT_ACCURACY_MAX)
        }

        // Vertical Accuracy
        val minVAcc = prefs.getFloat(KEY_V_ACCURACY_MIN, DEFAULT_V_ACCURACY_MIN)
        val maxVAcc = prefs.getFloat(KEY_V_ACCURACY_MAX, DEFAULT_V_ACCURACY_MAX)
        sliderVAccuracyRange.setValues(minVAcc, maxVAcc)
        updateVAccuracyLabel(minVAcc, maxVAcc)
        sliderVAccuracyRange.addOnChangeListener { slider, _, _ ->
            updateVAccuracyLabel(slider.values[0], slider.values[1])
        }
        btnResetVAccuracy.setOnClickListener {
            sliderVAccuracyRange.setValues(DEFAULT_V_ACCURACY_MIN, DEFAULT_V_ACCURACY_MAX)
            updateVAccuracyLabel(DEFAULT_V_ACCURACY_MIN, DEFAULT_V_ACCURACY_MAX)
        }
        
        // Jitter
        val jRad = prefs.getFloat(KEY_JITTER_RADIUS, DEFAULT_JITTER_RADIUS)
        sliderJitterRadius.value = jRad
        updateJitterLabel(jRad)
        sliderJitterRadius.addOnChangeListener { _, value, _ ->
            updateJitterLabel(value)
        }
        btnResetJitter.setOnClickListener {
            sliderJitterRadius.value = DEFAULT_JITTER_RADIUS
            updateJitterLabel(DEFAULT_JITTER_RADIUS)
        }

        btnSave.setOnClickListener {
            handleSave(
                etLatitude.text.toString(),
                etLongitude.text.toString(),
                sliderAccuracyRange.values[0],
                sliderAccuracyRange.values[1],
                sliderVAccuracyRange.values[0],
                sliderVAccuracyRange.values[1],
                sliderJitterRadius.value
            )
        }
    }

    private fun updatePreview() {
        if (!::tvPreviewCoords.isInitialized) return
        
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val baseLat = prefs.getString(KEY_LAT, DEFAULT_LAT.toString())?.toDoubleOrNull() ?: DEFAULT_LAT
        val baseLong = prefs.getString(KEY_LONG, DEFAULT_LONG.toString())?.toDoubleOrNull() ?: DEFAULT_LONG
        val radius = prefs.getFloat(KEY_JITTER_RADIUS, DEFAULT_JITTER_RADIUS).toDouble()

        if (radius <= 0) {
            tvPreviewCoords.text = "%.6f, %.6f".format(baseLat, baseLong)
            return
        }

        val latOffset = (Random.nextDouble() * 2 - 1) * (radius / 111111.0)
        val finalLat = baseLat + latOffset
        val lonOffset = (Random.nextDouble() * 2 - 1) * (radius / (111111.0 * cos(Math.toRadians(finalLat))))
        val finalLong = baseLong + lonOffset

        tvPreviewCoords.text = "%.6f, %.6f".format(finalLat, finalLong)
    }

    private fun formatValue(v: Float): String {
        return if (v % 1f == 0f) "${v.toInt()}m" else "%.1fm".format(v)
    }

    private fun updateAccuracyLabel(min: Float, max: Float) {
        tvAccuracyRangeVal.text = getString(R.string.label_accuracy_range_val, formatValue(min), formatValue(max))
    }

    private fun updateVAccuracyLabel(min: Float, max: Float) {
        tvVAccuracyRangeVal.text = getString(R.string.label_v_accuracy_range_val, formatValue(min), formatValue(max))
    }

    private fun updateJitterLabel(radius: Float) {
        tvJitterRadiusVal.text = getString(R.string.label_jitter_radius_val, formatValue(radius))
    }

    private fun updateModuleStatusUI() {
        val officialScope = getOfficialScope().filter { it != packageName }
        val isActive = (mXposedService != null && officialScope.isNotEmpty()) || checkSelfActive()

        if (isActive) {
            tvModuleStatus.text = getString(R.string.status_module_active)
            val activeColor = MaterialColors.getColor(tvModuleStatus, androidx.appcompat.R.attr.colorPrimary)
            tvModuleStatus.setTextColor(activeColor)
            cardModuleStatus.strokeColor = activeColor
            refreshLSPosedScope()
        } else {
            tvModuleStatus.text = getString(R.string.status_module_inactive)
            val inactiveColor = MaterialColors.getColor(tvModuleStatus, com.google.android.material.R.attr.colorOnSurfaceVariant)
            val strokeColor = MaterialColors.getColor(tvModuleStatus, com.google.android.material.R.attr.colorOutline)
            tvModuleStatus.setTextColor(inactiveColor)
            cardModuleStatus.strokeColor = strokeColor
            cardScopedApps.visibility = View.GONE
        }
    }

    private fun setupTitleSpannable(tvTitle: TextView) {
        val titleText = tvTitle.text.toString()
        val spannable = SpannableStringBuilder(titleText)
        val primaryColor = MaterialColors.getColor(
            this, 
            androidx.appcompat.R.attr.colorPrimary,
            "#FF6200EE".toColorInt()
        )
        spannable.setSpan(
            ForegroundColorSpan(primaryColor),
            0, 1,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        tvTitle.text = spannable
    }

    private fun handleSave(
        latStr: String, 
        lonStr: String, 
        minA: Float, 
        maxA: Float,
        minVA: Float,
        maxVA: Float,
        jRad: Float
    ) {
        val lat = latStr.toDoubleOrNull()
        val lon = lonStr.toDoubleOrNull()

        if (lat != null && lon != null) {
            if (lat in -90.0..90.0 && lon in -180.0..180.0) {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().apply {
                    putString(KEY_LAT, latStr)
                    putString(KEY_LONG, lonStr)
                    putFloat(KEY_ACCURACY_MIN, minA)
                    putFloat(KEY_ACCURACY_MAX, maxA)
                    putFloat(KEY_V_ACCURACY_MIN, minVA)
                    putFloat(KEY_V_ACCURACY_MAX, maxVA)
                    putFloat(KEY_JITTER_RADIUS, jRad)
                    apply()
                }
                showToast(getString(R.string.toast_save_success))
            } else {
                showToast(getString(R.string.toast_invalid_range), true)
            }
        } else {
            showToast(getString(R.string.toast_invalid_input))
        }
    }

    private fun showToast(message: String, isLong: Boolean = false) {
        Toast.makeText(this, message, if (isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }

    private fun checkSelfActive(): Boolean = false

    private fun getOfficialScope(): List<String> {
        return try {
            mXposedService?.scope ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun refreshLSPosedScope() {
        layoutScopedApps.removeAllViews()
        val officialScope = getOfficialScope().filter { it != packageName }
        if (officialScope.isNotEmpty()) {
            officialScope.forEach { addAppIconToLayout(it) }
            cardScopedApps.visibility = View.VISIBLE
        } else {
            cardScopedApps.visibility = View.GONE
        }
    }

    private fun addAppIconToLayout(pkgName: String) {
        try {
            val icon = packageManager.getApplicationIcon(pkgName)
            val size = (40 * resources.displayMetrics.density).toInt()
            val imageView = ImageView(this).apply {
                val params = LinearLayout.LayoutParams(size, size)
                params.setMargins(0, 0, 16, 0)
                layoutParams = params
                setImageDrawable(icon)
                contentDescription = pkgName
                setOnClickListener { Toast.makeText(this@MainActivity, pkgName, Toast.LENGTH_SHORT).show() }
            }
            layoutScopedApps.addView(imageView)
        } catch (_: Exception) {}
    }
}
