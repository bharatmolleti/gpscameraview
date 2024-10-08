package com.example.gpscameraview

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*

class MainActivity : AppCompatActivity() {
    private lateinit var cameraPreview: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var switch: Switch
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    var isWithinRange = false
    public var currentLocation = ""
    private lateinit var sharedPref: SharedPreferences
    private lateinit var editTextLat: EditText
    private lateinit var editTextLong: EditText
    private lateinit var editTextDist: EditText
    /*

     Current 17.5211137, 78.4195162
 mPreviousPosition == bounds[l=0.00 t=-79.00 r=1384.00 b=959.00]
 Current 17.5211145, 78.4195138
 mPreviousPosition == bounds[l=0.00 t=-79.00 r=1384.00 b=959.00]
 Current 17.5211191, 78.4195027
 mPreviousPosition == bounds[l=0.00 t=-79.00 r=1384.00 b=959.00]
 Current 17.5211138, 78.4195145
 mPreviousPosition == bounds[l=0.00 t=-79.00 r=1384.00 b=959.00]
 Current 17.5211146, 78.419513
 mPreviousPosition == bounds[l=0.00 t=-79.00 r=1384.00 b=959.00]
 Current 17.5211144, 78.4195148
     */
    // Coordinates for the specific GPS range
    private var targetLatitude = 17.5211 // Example latitude
    private var targetLongitude = 78.4195// Example longitude
    private var gpsThresholdMeters = 5.0 // Accuracy threshold in meters

    public var debugEnabled = false //

    private val NUM_POINTS = "NUM_POINTS";
    private val COORDINATES = "COORDINATES";

    public fun readCurrentId(activity: Activity) {
        val sharedPref = activity.getPreferences(Context.MODE_PRIVATE) ?: return
        val highScore = sharedPref.getInt(NUM_POINTS, 0)
    }

    public fun updateCurrentId(coordinates: String) {
        val highScore = sharedPref.getInt(NUM_POINTS, 0)
        with (sharedPref.edit()) {
            putInt(NUM_POINTS, (highScore +1))
            apply()
        }
        val coord = sharedPref.getString(COORDINATES + highScore, coordinates);
        with (sharedPref.edit()) {
            putInt(NUM_POINTS, (highScore +1))
            apply()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        sharedPref = this.getPreferences(Context.MODE_PRIVATE)
        cameraPreview = findViewById(R.id.cameraPreview)
        overlayView = findViewById(R.id.overlay);
        editTextLat = findViewById(R.id.editTextlat);
        editTextLat.setText(targetLatitude.toString())
        editTextLat.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            val value = s?.toString()?.toDouble();
            if (value != null && value != targetLatitude) {
                targetLatitude = value;
            }
        }});
        editTextLong = findViewById(R.id.editTextlong);
        editTextLong.setText(targetLongitude.toString())
        editTextLong.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val value = s?.toString()?.toDouble();
                if (value != null && value != targetLongitude) {
                    targetLongitude = value;
                }
            }});
        editTextDist = findViewById(R.id.editTextDist);
        editTextDist.setText(gpsThresholdMeters.toString())
        editTextDist.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val value = s?.toString()?.toDouble();
                if (value != null && value != gpsThresholdMeters) {
                    gpsThresholdMeters = value;
                }
            }});
        switch = findViewById(R.id.switch1);
        switch.setOnCheckedChangeListener{_, checked ->
            debugEnabled = checked;
            if (checked) editTextLat.visibility = View.VISIBLE;
            else editTextLat.visibility = View.INVISIBLE;
            editTextLong.visibility = if(checked) View.VISIBLE else View.INVISIBLE;
            editTextDist.visibility = if(checked) View.VISIBLE else View.INVISIBLE;
        }

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Request camera and location permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION), 100)
        } else {
            startCameraPreview()
            startLocationUpdates()
        }
    }

    private fun startCameraPreview() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Bind the lifecycle of the camera to the lifecycle of the activity
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(cameraPreview.surfaceProvider) }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            //.setMinUpdateDistanceMeters(5.0f)
            .build()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            for (location in locationResult.locations) {
                Log.d("GPS", "Current " + location.latitude + ", " + location.longitude)
                val distance = FloatArray(1)
                android.location.Location.distanceBetween(
                    location.latitude, location.longitude,
                    targetLatitude, targetLongitude,
                    distance
                )
                currentLocation = "" + location.latitude + ", " + location.longitude + " : "  + distance[0];
                Log.d("GPS ", "Distance is : " + distance[0] + " gpsThreshold " + gpsThresholdMeters);
                isWithinRange = distance[0] < gpsThresholdMeters
                cameraPreview.invalidate()  // Redraw the view with the overlay
                overlayView.invalidate()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCameraPreview()
            startLocationUpdates()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}

