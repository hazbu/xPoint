package com.hazbu.xpoint

import android.os.Bundle
import android.util.Log
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
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
import com.google.android.material.textfield.TextInputEditText
import com.hazbu.xpoint.Constants.DEFAULT_LAT
import com.hazbu.xpoint.Constants.DEFAULT_LONG
import com.hazbu.xpoint.Constants.KEY_LAT
import com.hazbu.xpoint.Constants.KEY_LONG
import com.hazbu.xpoint.Constants.PREFS_NAME
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

class MainActivity : AppCompatActivity(), XposedServiceHelper.OnServiceListener {

    private lateinit var tvModuleStatus: TextView
    private lateinit var cardModuleStatus: com.google.android.material.card.MaterialCardView
    private lateinit var cardScopedApps: com.google.android.material.card.MaterialCardView
    private lateinit var layoutScopedApps: LinearLayout
    private var mXposedService: XposedService? = null

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
        val etLatitude = findViewById<TextInputEditText>(R.id.et_latitude)
        val etLongitude = findViewById<TextInputEditText>(R.id.et_longitude)
        val btnSave = findViewById<android.widget.Button>(R.id.btn_save)
        val tvTitle = findViewById<TextView>(R.id.tv_title)
        
        tvModuleStatus = findViewById(R.id.tv_module_status)
        cardModuleStatus = findViewById(R.id.card_module_status)
        cardScopedApps = findViewById(R.id.card_scoped_apps)
        layoutScopedApps = findViewById(R.id.layout_scoped_apps)

        setupTitleSpannable(tvTitle)
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        etLatitude.setText(prefs.getString(KEY_LAT, DEFAULT_LAT.toString()))
        etLongitude.setText(prefs.getString(KEY_LONG, DEFAULT_LONG.toString()))
        btnSave.setOnClickListener {
            handleSave(etLatitude.text.toString(), etLongitude.text.toString())
        }
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

    private fun handleSave(latStr: String, lonStr: String) {
        val lat = latStr.toDoubleOrNull()
        val lon = lonStr.toDoubleOrNull()
        if (lat != null && lon != null) {
            if (lat in -90.0..90.0 && lon in -180.0..180.0) {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().apply {
                    putString(KEY_LAT, latStr)
                    putString(KEY_LONG, lonStr)
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
