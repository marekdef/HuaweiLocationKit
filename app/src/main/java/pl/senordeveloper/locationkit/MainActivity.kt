package pl.senordeveloper.locationkit

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.content.Context
import android.content.IntentSender.SendIntentException
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.location.LocationManager
import android.location.LocationManager.GPS_PROVIDER
import android.location.LocationManager.NETWORK_PROVIDER
import android.location.LocationManager.PASSIVE_PROVIDER
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
//import com.google.android.gms.common.ConnectionResult.RESOLUTION_REQUIRED
//import com.google.android.gms.common.api.ApiException
//import com.google.android.gms.common.api.ResolvableApiException
//import com.google.android.gms.location.FusedLocationProviderClient
//import com.google.android.gms.location.LocationAvailability
//import com.google.android.gms.location.LocationCallback
//import com.google.android.gms.location.LocationRequest
//import com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
//import com.google.android.gms.location.LocationResult
//import com.google.android.gms.location.LocationServices
//import com.google.android.gms.location.LocationSettingsRequest
import pl.senordeveloper.locationkit.databinding.ActivityMainBinding
import com.huawei.hms.common.ApiException
import com.huawei.hms.common.ResolvableApiException
import com.huawei.hms.location.FusedLocationProviderClient
import com.huawei.hms.location.LocationAvailability
import com.huawei.hms.location.LocationCallback
import com.huawei.hms.location.LocationRequest
import com.huawei.hms.location.LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
import com.huawei.hms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
import com.huawei.hms.location.LocationResult
import com.huawei.hms.location.LocationServices
import com.huawei.hms.location.LocationSettingsRequest
import com.huawei.hms.location.LocationSettingsStatusCodes
import com.huawei.hms.location.LocationSettingsStatusCodes.RESOLUTION_REQUIRED
import com.huawei.location.nlp.network.response.Location
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import org.slf4j.LoggerFactory


private const val REQUEST_CODE: Int = 0xFF

class MainActivity : AppCompatActivity() {
    private val LOGGER = LoggerFactory.getLogger(MainActivity::class.java)

    private val adapter = LogAdapter()

    private val settingsClient by lazy {
        LocationServices.getSettingsClient(this)
    }

    // Location interaction object.
    private val fusedLocationProviderClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationAvailability(locationAvailability: LocationAvailability) {
            adapter.add("isLocationAvailable" to locationAvailability.isLocationAvailable.toString())
            LOGGER.info("onLocationAvailability isLocationAvailable=${locationAvailability.isLocationAvailable} locationStatus=${locationAvailability}")
            mainActivityMainBinding.textResult.text = "isLocationAvailable=${locationAvailability.isLocationAvailable}"
        }

        override fun onLocationResult(locationResult: LocationResult) {
            LOGGER.info("onLocationResult($locationResult)")
            adapter.add("onLocationResult" to with(locationResult.lastLocation) { this?.let { "${it.latitude}x${it.longitude}" } ?: "null" })
            adapter.add("onLocationResult" to locationResult.locations.size.toString())
            mainActivityMainBinding.textResult.text =
                "onLocationResult = ${with(locationResult.lastLocation) { this?.let { "${it.latitude}x${it.longitude}" } ?: "null" }}"
        }
    }


    // Location request object.
    private val locationRequest: LocationRequest by lazy {
        LocationRequest().apply {
            interval = 10_000
            maxWaitTime = 60_000
            priority = PRIORITY_HIGH_ACCURACY
        }
    }

    private val mainActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(mainActivityMainBinding.root)

        mainActivityMainBinding.recyclerViewLogs.layoutManager = LinearLayoutManager(this)
        mainActivityMainBinding.recyclerViewLogs.itemAnimator = DefaultItemAnimator()
        mainActivityMainBinding.recyclerViewLogs.adapter = adapter

        mainActivityMainBinding.buttonRequestUpdates.setOnClickListener {
            locationUpdates()
        }

        mainActivityMainBinding.buttonRequestPermissions.setOnClickListener {
            askForPermissions()
        }

        mainActivityMainBinding.buttonLocationManager.setOnClickListener {
            locationManager()
        }

        mainActivityMainBinding.buttonLastLocation.setOnClickListener {
            lastLocation()
        }

        mainActivityMainBinding.buttonLocationAvailability.setOnClickListener {
            locationAvailability()
        }
    }

    private fun locationManager() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager


        val gps = LocationManagerCompat.hasProvider(locationManager, GPS_PROVIDER)
        val network = LocationManagerCompat.hasProvider(locationManager, NETWORK_PROVIDER)
        val passive = LocationManagerCompat.hasProvider(locationManager, PASSIVE_PROVIDER)

        adapter.add("network providers" to "$gps $network $passive")
        mainActivityMainBinding.textResult.text = "isLocationEnabled = ${LocationManagerCompat.isLocationEnabled(locationManager)}\n" +
                "GPS_PROVIDER=$gps\n" +
                "NETWORK_PROVIDER=$network\n" +
                "PASSIVE_PROVIDER=${passive}"
    }

    @SuppressLint("MissingPermission")
    private fun locationAvailability() {
        fusedLocationProviderClient.locationAvailability.addOnSuccessListener {
            adapter.add("isLocationAvailable" to it.isLocationAvailable.toString())
            mainActivityMainBinding.textResult.text =
                "locationAvailability isLocationAvailable = ${it.isLocationAvailable} locationStatus = ${it}"
            LOGGER.warn("locationAvailability.addOnSuccessListener isLocationAvailable = ${it.isLocationAvailable} locationStatus = ${it}")
        }.addOnFailureListener { exception ->
            adapter.add("locationAvailability.addOnFailureListener" to exception.javaClass.simpleName)
            LOGGER.warn("locationAvailability.addOnFailureListener", exception)
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastLocation() {
        fusedLocationProviderClient.lastLocation.addOnSuccessListener {
            adapter.add("lastLocation.addOnSuccessListener" to "${it.latitude},${it.longitude}")
            mainActivityMainBinding.textResult.text = "lastLocation = ${it.latitude}, ${it.longitude}}"
            LOGGER.info("fusedLocationProviderClient.lastLocation.addOnSuccessListener($it)")
        }.addOnFailureListener {
            adapter.add("lastLocation.addOnFailureListener" to it.javaClass.simpleName)
            mainActivityMainBinding.textResult.text = "lastLocation = ${it.javaClass.simpleName} ${it.message}"
            LOGGER.warn("fusedLocationProviderClient.lastLocation.addOnFailureListener", it)
        }
    }

    @SuppressLint("MissingPermission")
    private fun locationUpdates() {
        val builder = LocationSettingsRequest.Builder()
        builder.addLocationRequest(locationRequest)
        val locationSettingsRequest = builder.build()
// Check the device location settings.
        settingsClient.checkLocationSettings(locationSettingsRequest) // Define callback for success in checking the device location settings.
            .addOnSuccessListener { location ->
                adapter.add("checkLocationSettings.addOnSuccessListener" to with(location.locationSettingsStates) { "${this?.isLocationUsable} ${this?.isLocationPresent}" })
                LOGGER.info("checkLocationSettings.addOnSuccessListener ${
                    with(location.locationSettingsStates) {
                        "isLocationUsable=${this?.isLocationUsable} isLocationPresent=${this?.isLocationPresent}"
                    }
                }")
                val flow = callbackFlow<android.location.Location?> {
                    val locationCallback = object : LocationCallback() {
                        override fun onLocationResult(locationResult: LocationResult?) {
                            adapter.add("onLocationResult" to locationResult.toString())
                            if (locationResult != null)
                                trySend(locationResult.lastLocation)
                        }

                        override fun onLocationAvailability(locationAvailability: LocationAvailability?) {
                            adapter.add("onLocationAvailability" to locationAvailability?.isLocationAvailable.toString())
                            if (locationAvailability?.isLocationAvailable == true) {
                                fusedLocationProviderClient.lastLocation.addOnSuccessListener { location ->
                                    adapter.add("lastLocation.addOnSuccessListener" to location.toString())
                                    if (location != null) {
                                        trySend(location)
                                    }
                                }
                            }
                        }
                    }
                    fusedLocationProviderClient
                        .requestLocationUpdates(
                            locationRequest,
                            locationCallback,
                            Looper.getMainLooper()
                        ) // Define callback for success in requesting location updates.
                        .addOnSuccessListener {
                            adapter.add("requestLocationUpdates" to "addOnSuccessListener")
                            LOGGER.info("requestLocationUpdates.addOnSuccessListener")
                        }.addOnFailureListener { exception ->
                            adapter.add("requestLocationUpdates.addOnFailureListener" to "${exception.javaClass.simpleName}")
                            LOGGER.warn("requestLocationUpdates.addOnFailureListener", exception)
                        }
                    awaitClose {
                        adapter.add("flow" to "closed")
                        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
                    }
                }
                lifecycleScope.launchWhenResumed {
                    flow.collect { location ->
                        adapter.add("flow" to with(location) { this?.let { "${it.latitude}x${it.longitude}" } ?: "null"})
                    }
                }

                // Initiate location requests when the location settings meet the requirements.

            } // Define callback for failure in checking the device location settings.
            .addOnFailureListener { e ->
                // Device location settings do not meet the requirements.
                val statusCode = (e as ApiException).statusCode
                LOGGER.info("checkLocationSettings.addOnFailureListener $statusCode", e)
                when (statusCode) {
                    RESOLUTION_REQUIRED -> try {
                        val rae = e as ResolvableApiException
                        // Call startResolutionForResult to display a pop-up asking the user to enable related permission.
                        rae.startResolutionForResult(this@MainActivity, 0)
                    } catch (sie: SendIntentException) {
                        LOGGER.warn("SendIntentException", sie)
                    }
                }
            }
    }


    private fun askForPermissions() {
        val finePermission = ActivityCompat.checkSelfPermission(
            this,
            ACCESS_FINE_LOCATION
        )
        val coarsePermission = ActivityCompat.checkSelfPermission(
            this,
            ACCESS_COARSE_LOCATION
        )

        mainActivityMainBinding.textResult.text = "fine = ${finePermission.permission} coarse=${coarsePermission.permission}"
        if (finePermission != PERMISSION_GRANTED
            && coarsePermission != PERMISSION_GRANTED
        ) {
            val strings = arrayOf<String>(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)
            ActivityCompat.requestPermissions(this, strings, REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        mainActivityMainBinding.textResult.text = "${permission(permissions, grantResults)}"
    }

    private fun permission(permissions: Array<out String>, grantResults: IntArray) = permissions.mapIndexed { index, permission ->
        "$permission = ${grantResults[index].permission}"
    }
}


class LogAdapter : RecyclerView.Adapter<LogHolder>() {
    private val items = mutableListOf<Pair<String, String>>()
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogHolder =
        LogHolder(LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false))

    override fun onBindViewHolder(holder: LogHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun add(pair: Pair<String, String>) {
        items.add(0, pair)
        notifyItemInserted(0)
    }

}

class LogHolder(view: View) : RecyclerView.ViewHolder(view) {
    private val title = view.findViewById<TextView>(android.R.id.text1)
    private val content = view.findViewById<TextView>(android.R.id.text2)

    fun bind(pair: Pair<String, String>) {
        title.text = pair.first
        content.text = pair.second
    }
}


private val Int.permission: String
    get() = when (this) {
        PERMISSION_GRANTED -> "GRANTED"
        PERMISSION_DENIED -> "DENIED"
        else -> toString()
    }

