package com.example.eatical_mobile_app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.eatical_mobile_app.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.osmdroid.util.GeoPoint

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var longitude: Double = 0.0
    private var latitude: Double = 0.0
    private var lastButtonClicked: String? = null

    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
        if(it.resultCode == Activity.RESULT_OK){
            val data = it?.data
            if(data != null) {
                val coordinates = data.getParcelableExtra<GeoPoint>("coordinates")
                if (coordinates != null) {
                    longitude = coordinates.longitude
                    latitude = coordinates.latitude
                    Toast.makeText(
                        this,
                        "New Location:($longitude, $latitude)",
                        Toast.LENGTH_SHORT
                    ).show()
                } else throw Exception(R.string.coordinates_null.toString())
            }else throw Exception(R.string.data_null.toString())
        }else {
            println(R.string.user_did_not_choose_coordinates)
            Toast.makeText(this, R.string.device_location_set, Toast.LENGTH_SHORT).show()
        }
    }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                getLocation()
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                getLocation()
            }
            else -> {
                showDialogueAndFinish(R.string.location_not_granted_error)
            }
        }
    }

    private val cameraPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            lastButtonClicked?.let { openPhotoIntent(it) }
        } else {
            showDialogue(R.string.camera_permission_not_granted_error)
        }
    }

    private val galleryPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openPhotoIntent("gallery")
        } else {
            showDialogue(R.string.gallery_permission_not_granted_error)
        }
    }

    private val bodySensorsPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.setState(MainStates.GETTING_CAMERA_PERMISSION)
        } else {
            showDialogue(R.string.camera_permission_not_granted_error)
        }
    }

    private fun getLocation() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                longitude = location.longitude
                latitude = location.latitude
                Toast.makeText(this, "$longitude $latitude", Toast.LENGTH_SHORT).show()
            } else {
                showDialogueAndFinish(R.string.location_not_on_error)
            }
        }
    }

    private fun openPhotoIntent(source: String) {
        val intent = Intent(this, PhotoActivity::class.java)
        intent.putExtra("source", source)
        intent.putExtra("type", getSelectedType())
        intent.putExtra("longitude", longitude)
        intent.putExtra("latitude", latitude)
        startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        installSplashScreen().apply {
            setKeepOnScreenCondition {
                viewModel.state.value == MainStates.LOADING
            }
        }

        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        observeButtons()
        observeCheckboxes()
        observeState()
    }

    private fun observeButtons() = with(binding) {
        cameraButton.setOnClickListener {
            lastButtonClicked = "camera"
            if (optionIsSelected()) viewModel.setState(MainStates.GETTING_CAMERA_PERMISSION)
        }

        galleryButton.setOnClickListener {
            if (optionIsSelected()) viewModel.setState(MainStates.GETTING_GALLERY_PERMISSION)
        }

        intervalShooterButton.setOnClickListener {
            lastButtonClicked = "intervalShooter"
            if (optionIsSelected()) viewModel.setState(MainStates.GETTING_BODY_SENSORS_PERMISSION)
        }

        mapButton.setOnClickListener{
            viewModel.setState(MainStates.CHOOSE_LOCATION)
        }

        resetButton.setOnClickListener {
            viewModel.setState(MainStates.RESET_LOCATION)
        }
    }

    private fun observeCheckboxes() = with(binding) {
        restaurantCheckbox.setOnClickListener {
            removeOptions()
            restaurantCheckbox.isChecked = true
        }

        foodCheckbox.setOnClickListener {
            removeOptions()
            foodCheckbox.isChecked = true
        }

        menuCheckbox.setOnClickListener {
            removeOptions()
            menuCheckbox.isChecked = true
        }
    }

    private fun observeState() {
        viewModel.state.observe(this) { state ->
            when (state) {
                MainStates.LOADING -> Unit
                MainStates.INITIAL -> Unit
                MainStates.GETTING_LOCATION_PERMISSION -> getLocationPermission()
                MainStates.GETTING_CAMERA_PERMISSION -> getCameraPermission()
                MainStates.GETTING_GALLERY_PERMISSION -> getGalleryPermission()
                MainStates.GETTING_BODY_SENSORS_PERMISSION -> getBodySensorsPermission()
                MainStates.CHOOSE_LOCATION -> getMap()
                MainStates.RESET_LOCATION -> resetLocation()
            }
        }
    }

    private fun getLocationPermission() {
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun getCameraPermission() {
        cameraPermissionRequest.launch(Manifest.permission.CAMERA)
    }

    private fun getGalleryPermission() {
        galleryPermissionRequest.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun getMap(){
        val intent = Intent(this, MapActivity::class.java)
        intent.putExtra("latitude", latitude)
        intent.putExtra("longitude", longitude)
        launcher.launch(intent)
    }

    private fun resetLocation(){
        getLocation()
    }

    private fun getBodySensorsPermission() {
        bodySensorsPermissionRequest.launch(Manifest.permission.BODY_SENSORS)
    }

    private fun showDialogue(@StringRes resId: Int) {
        AlertDialog.Builder(this).setTitle(getString(R.string.app_name))
            .setMessage(getString(resId))
            .setNegativeButton(getString(R.string.okay)) { _, _ -> }
            .show()
    }

    private fun showDialogueAndFinish(@StringRes resId: Int) {
        AlertDialog.Builder(this).setTitle(getString(R.string.app_name))
            .setMessage(getString(resId))
            .setNegativeButton(getString(R.string.okay)) { _, _ ->
                finish()
            }.show()
    }

    private fun optionIsSelected(): Boolean {
        return if (binding.restaurantCheckbox.isChecked || binding.foodCheckbox.isChecked || binding.menuCheckbox.isChecked)
            true
        else {
            Toast.makeText(this, "Please select a type", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun removeOptions() = with(binding) {
        restaurantCheckbox.isChecked = false
        foodCheckbox.isChecked = false
        menuCheckbox.isChecked = false
    }

    private fun getSelectedType(): String = with(binding) {
        if (restaurantCheckbox.isChecked) return "restaurant"
        else if (foodCheckbox.isChecked) return "food"
        else return "menu"
    }
}