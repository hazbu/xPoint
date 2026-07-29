package com.hazbu.xpoint

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.Button
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

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        setupWindowInsets()
        setupUI()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
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
        val btnSave = findViewById<Button>(R.id.btn_save)
        val tvTitle = findViewById<TextView>(R.id.tv_title)

        setupTitleSpannable(tvTitle)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        
        // Load saved values
        etLatitude.setText(prefs.getString(KEY_LAT, DEFAULT_LAT.toString()))
        etLongitude.setText(prefs.getString(KEY_LONG, DEFAULT_LONG.toString()))

        btnSave.setOnClickListener {
            handleSave(etLatitude.text.toString(), etLongitude.text.toString())
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
            0, 1, // index of 'x'
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
}
