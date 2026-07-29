package com.hazbu.xpoint

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

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

        val etLatitude = findViewById<TextInputEditText>(R.id.et_latitude)
        val etLongitude = findViewById<TextInputEditText>(R.id.et_longitude)
        val btnSave = findViewById<Button>(R.id.btn_save)

        val prefs = getSharedPreferences("fake_location_prefs", Context.MODE_PRIVATE)
        
        // Load saved values
        etLatitude.setText(prefs.getString("lat", "0.0"))
        etLongitude.setText(prefs.getString("long", "0.0"))

        btnSave.setOnClickListener {
            val latStr = etLatitude.text.toString()
            val lonStr = etLongitude.text.toString()

            val lat = latStr.toDoubleOrNull()
            val lon = lonStr.toDoubleOrNull()

            if (lat != null && lon != null) {
                if (lat in -90.0..90.0 && lon in -180.0..180.0) {
                    prefs.edit().apply {
                        putString("lat", latStr)
                        putString("long", lonStr)
                        apply()
                    }
                    Toast.makeText(this, "Coordinates Saved Successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Invalid range (Lat: -90 to 90, Long: -180 to 180)", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "Please enter valid decimal numbers", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
