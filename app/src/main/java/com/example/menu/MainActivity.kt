package com.example.menu

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Handle the splash screen transition.
        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Close app when car image is clicked
        val carImage = findViewById<ImageView>(R.id.car_image)
        carImage.setOnClickListener {
            finishAndRemoveTask()
        }

        // Launch WhatsApp with icon 1 and close this app
        val whatsappIcon = findViewById<ImageView>(R.id.icon_1)
        whatsappIcon.setOnClickListener {
            val packageName = "com.whatsapp"
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                try {
                    startActivity(intent)
                    finishAndRemoveTask()
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(this, "Whatsapp non installato", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Whatsapp non installato", Toast.LENGTH_SHORT).show()
            }
        }

        // Launch Maps + More with icon 2 and close this app
        val mapsMoreIcon = findViewById<ImageView>(R.id.icon_2)
        mapsMoreIcon.setOnClickListener {
            val packageName = "de.volkswagen.mapsandmore"
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                try {
                    startActivity(intent)
                    finishAndRemoveTask()
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(this, "Maps + More non installato", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Maps + More non installato", Toast.LENGTH_SHORT).show()
            }
        }

        // Handle Torque app with icon 3
        val torqueIcon = findViewById<ImageView>(R.id.icon_3)
        // Short click: Launch Torque and close this app
        torqueIcon.setOnClickListener {
            val packageName = "org.prowl.torque"
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                try {
                    startActivity(intent)
                    finishAndRemoveTask()
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(this, "Torque non installato", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Torque non installato", Toast.LENGTH_SHORT).show()
            }
        }

        // Launch Google Maps with icon 4 and close this app
        val googleMapsIcon = findViewById<ImageView>(R.id.icon_4)
        googleMapsIcon.setOnClickListener {
            val packageName = "com.google.android.apps.maps"
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                try {
                    startActivity(intent)
                    finishAndRemoveTask()
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(this, "Google Maps non installato", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Google Maps non installato", Toast.LENGTH_SHORT).show()
            }
        }

        // Close app with other icons
        val closeAppListener = View.OnClickListener {
            finishAndRemoveTask()
        }

        findViewById<ImageView>(R.id.icon_5).setOnClickListener(closeAppListener)
        findViewById<ImageView>(R.id.icon_6).setOnClickListener(closeAppListener)
    }
}