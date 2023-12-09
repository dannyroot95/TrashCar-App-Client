package com.optic.uberclonekotlin.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.service.voice.VoiceInteractionService.isActiveService
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.easywaylocation.EasyWayLocation
import com.example.easywaylocation.Listener
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.maps.android.SphericalUtil
import com.optic.uberclonekotlin.R
import com.optic.uberclonekotlin.databinding.ActivityMapBinding
import com.optic.uberclonekotlin.databinding.PopupBinding
import com.optic.uberclonekotlin.databinding.RutasBinding
import com.optic.uberclonekotlin.fragments.ModalBottomSheetMenu
import com.optic.uberclonekotlin.models.Coverage
import com.optic.uberclonekotlin.models.DriverLocation
import com.optic.uberclonekotlin.models.Microroutes
import com.optic.uberclonekotlin.providers.AuthProvider
import com.optic.uberclonekotlin.providers.ClientProvider
import com.optic.uberclonekotlin.providers.GeoProvider
import com.optic.uberclonekotlin.services.TrashCarService
import com.optic.uberclonekotlin.utils.CarMoveAnim
import com.optic.uberclonekotlin.utils.Constants
import com.optic.uberclonekotlin.utils.TinyDB
import org.imperiumlabs.geofirestore.callbacks.GeoQueryEventListener
import java.lang.Math.*
import kotlin.math.pow

class MapActivity : AppCompatActivity(), OnMapReadyCallback, Listener {

    private lateinit var binding: ActivityMapBinding
    private var googleMap: GoogleMap? = null
    private var easyWayLocation: EasyWayLocation? = null
    private var myLocationLatLng: LatLng? = null
    private val geoProvider = GeoProvider()
    private val authProvider = AuthProvider()
    private val clientProvider = ClientProvider()

    // GOOGLE PLACES
    private var places: PlacesClient? = null
    private var autocompleteOrigin: AutocompleteSupportFragment? = null
    private var autocompleteDestination: AutocompleteSupportFragment? = null
    private var originName = ""
    private var originLatLng: LatLng? = null
    private var isLocationEnabled = false
    private val driverMarkers = ArrayList<Marker>()
    private val driversLocation = ArrayList<DriverLocation>()
    private val modalMenu = ModalBottomSheetMenu()
    private var myLocationMarker: Marker? = null
    private lateinit var popup : PopupBinding
    private lateinit var rutas : RutasBinding
    private lateinit var dialog: Dialog
    private lateinit var dialogStreets: Dialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)
        var active = TinyDB(this)
        popup = PopupBinding.inflate(layoutInflater)
        rutas = RutasBinding.inflate(layoutInflater)

        dialog = Dialog(this)
        dialog.window?.setBackgroundDrawable(ColorDrawable(0))
        dialog.setContentView(popup.root)

        dialogStreets = Dialog(this)
        dialogStreets.window?.setBackgroundDrawable(ColorDrawable(0))
        dialogStreets.setContentView(rutas.root)

        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        if(!isGpsEnabled(this)){
            showEnableGpsDialog(this)
        }

        binding.btnNotificationOn.setOnClickListener {
            active.putString(Constants.ACTIVE, Constants.ACTIVE_NOTIFICATION)
            binding.btnNotificationOff.visibility = View.VISIBLE
            binding.btnNotificationOn.visibility = View.GONE

            startService(Intent(this, TrashCarService::class.java))

        }

        binding.btnNotificationOff.setOnClickListener {
            active.putString(Constants.ACTIVE, Constants.DISABLE_NOTIFICATION)
            binding.btnNotificationOff.visibility = View.GONE
            binding.btnNotificationOn.visibility = View.VISIBLE
            stopService(Intent(this, TrashCarService::class.java))
            //stopNotify
        }

        val locationRequest = LocationRequest.create().apply {
            interval = 0
            fastestInterval = 0
            priority = Priority.PRIORITY_HIGH_ACCURACY
            smallestDisplacement = 1f
        }

        easyWayLocation = EasyWayLocation(this, locationRequest, false, false, this)

        locationPermissions.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))

        createToken()

        binding.imageViewMenu.setOnClickListener { showModalMenu() }
        binding.btnRoutes.setOnClickListener {
            dialog.show()
        }
        popup.btnRequest.setOnClickListener {
            val meters = popup.etRadius.text.toString()
            if(meters != ""){
                getNearbyMicroroute(meters.toInt())
            }else{
                Toast.makeText(this,"Ingrese una distancia en metros!",Toast.LENGTH_SHORT).show()
            }
        }
    }

    val locationPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permission ->

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            when {
                permission.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                    Log.d("LOCALIZACION", "Permiso concedido")
                    easyWayLocation?.startLocation()
                }
                permission.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                    Log.d("LOCALIZACION", "Permiso concedido con limitacion")
                    easyWayLocation?.startLocation()
                }
                else -> {
                    Log.d("LOCALIZACION", "Permiso no concedido")
                }
            }
        }

      }

    private fun createToken() {
        clientProvider.createToken(authProvider.getId())
    }

    private fun showModalMenu() {
        modalMenu.show(supportFragmentManager, ModalBottomSheetMenu.TAG)
    }


    private fun getNearbyMicroroute(meters : Int){

        val myLatitude =  myLocationLatLng!!.latitude
        val myLongitude = myLocationLatLng!!.longitude
        val radius = meters/1000.0
        val locaDb  = TinyDB(this)

        if(myLatitude != 0.0 && myLongitude != 0.0 ){

            val currentPosition = LatLng(myLatitude, myLongitude)
            val db: FirebaseFirestore = FirebaseFirestore.getInstance()
            var idNearby = ""
            var turn = ""
            var streets : ArrayList<Coverage> = ArrayList<Coverage>()
            popup.btnRequest.visibility = View.GONE
            popup.pg.visibility = View.VISIBLE
            db.collection("microroutes").get().addOnSuccessListener {snapshot->
                for (i in snapshot.documents) {
                    val positions = mutableListOf<LatLng>()
                    val values = i.toObject(Microroutes::class.java)!!
                    streets = values.coverage
                    turn = values.turn!!
                    val pos = values.positions
                    for (z in pos){
                        val lat = z.lat!!
                        val lng = z.lng!!
                        positions.add(LatLng(lat, lng))
                    }
                    // Check if you are within the radius of any position in the current document
                    val isWithinRadius = positions.any { targetPosition ->
                        isWithinRadius(currentPosition, targetPosition, radius)
                    }
                    if (isWithinRadius) {
                        println("cerca -> ${values.id}.")
                        idNearby = values.id.toString()
                    } else {
                        println("Lejos -> ${values.id}.")
                    }
                }
                if(idNearby == ""){
                    popup.btnRequest.visibility = View.VISIBLE
                    popup.pg.visibility = View.GONE
                    Toast.makeText(this,"Sin resultados!",Toast.LENGTH_SHORT).show()
                }else{
                    popup.btnRequest.visibility = View.VISIBLE
                    popup.pg.visibility = View.GONE
                    popup.etRadius.setText("")
                    dialog.dismiss()
                    dialogStreets.show()
                    var pos = 0
                    rutas.streets.text = ""
                    streets.forEach { c ->
                        pos++
                        rutas.streets.append("${pos}.-"+"${c.avenue}\n")
                    }
                    if(turn == "M"){
                        rutas.turn.text = "Mañana"
                    }else{
                        rutas.turn.text = "Noche"
                    }

                    rutas.btnSave.setOnClickListener {
                        dialogStreets.dismiss()
                        locaDb.putString(Constants.LOCAL_DB_MICROROUTE,idNearby)
                        drawPolylineWithMarkers(idNearby,googleMap!!)
                        Toast.makeText(this,"Ruta guardada!",Toast.LENGTH_SHORT).show()
                        val i = Intent(this, MapActivity::class.java)
                        i.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(i)
                    }
                    rutas.btnCancel.setOnClickListener {
                        dialogStreets.dismiss()
                        Toast.makeText(this,"Cancelado!",Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }else{
            Toast.makeText(this,"Active su GPS!",Toast.LENGTH_SHORT).show()
        }
    }

    private fun getNearbyDrivers() {

        if (myLocationLatLng == null) return

        geoProvider.getNearbyDrivers(myLocationLatLng!!, 30.0).addGeoQueryEventListener(object: GeoQueryEventListener {

            override fun onKeyEntered(documentID: String, location: GeoPoint) {

                for (marker in driverMarkers) {
                    if (marker.tag != null) {
                        if (marker.tag == documentID) {
                            return
                        }
                    }
                }
                 // CREAMOS UN NUEVO MARCADOR PARA EL CONDUCTOR CONECTADO
                val driverLatLng = LatLng(location.latitude, location.longitude)
                val marker = googleMap?.addMarker(
                    MarkerOptions().position(driverLatLng).title("Carro basurero").icon(
                        BitmapDescriptorFactory.fromResource(R.drawable.uber_car)
                    )
                )

                marker?.tag = documentID
                driverMarkers.add(marker!!)

                val dl = DriverLocation()
                dl.id = documentID
                driversLocation.add(dl)
            }

            override fun onKeyExited(documentID: String) {
                for (marker in driverMarkers) {
                    if (marker.tag != null) {
                        if (marker.tag == documentID) {
                            marker.remove()
                            driverMarkers.remove(marker)
                            driversLocation.removeAt(getPositionDriver(documentID))
                            return
                        }
                    }
                }
            }

            override fun onKeyMoved(documentID: String, location: GeoPoint) {

                for (marker in driverMarkers) {

                    val start = LatLng(location.latitude, location.longitude)
                    var end: LatLng? = null
                    val position = getPositionDriver(marker.tag.toString())

                    if (marker.tag != null) {
                        if (marker.tag == documentID) {
//                            marker.position = LatLng(location.latitude, location.longitude)

                            if (driversLocation[position].latlng != null) {
                                end = driversLocation[position].latlng
                            }
                            driversLocation[position].latlng = LatLng(location.latitude, location.longitude)
                            if (end  != null) {
                                CarMoveAnim.carAnim(marker, end, start)
                            }

                        }
                    }
                }
            }

            override fun onGeoQueryError(exception: Exception) {

            }

            override fun onGeoQueryReady() {

            }

        })
    }

    private fun isWithinRadius(currentPosition: LatLng, targetPosition: LatLng, radius: Double): Boolean {
        val distance = haversine(
            currentPosition.latitude, currentPosition.longitude,
            targetPosition.latitude, targetPosition.longitude
        )
        return distance <= radius
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = toRadians(lat2 - lat1)
        val dLon = toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(toRadians(lat1)) * cos(toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * asin(sqrt(a))
        return 6371.0 * c // Earth radius in kilometers
    }

    private fun getPositionDriver(id: String): Int {
        var position = 0
        for (i in driversLocation.indices) {
            if (id == driversLocation[i].id) {
                position = i
                break
            }
        }
        return position
    }

    private fun onCameraMove() {
        googleMap?.setOnCameraIdleListener {
            try {
                val geocoder = Geocoder(this)
                originLatLng = googleMap?.cameraPosition?.target

                if (originLatLng != null) {
                    val addressList = geocoder.getFromLocation(originLatLng?.latitude!!, originLatLng?.longitude!!, 1)
                    if (addressList.size > 0) {
                        val city = addressList[0].locality
                        val address = addressList[0].getAddressLine(0)
                        originName = "$address $city"
                        autocompleteOrigin?.setText("$address $city")
                    }
                }

            } catch (e: Exception) {
                Log.d("ERROR", "Mensaje error: ${e.message}")
            }
        }
    }



    private fun limitSearch() {
        val northSide = SphericalUtil.computeOffset(myLocationLatLng, 5000.0, 0.0)
        val southSide = SphericalUtil.computeOffset(myLocationLatLng, 5000.0, 180.0)

        autocompleteOrigin?.setLocationBias(RectangularBounds.newInstance(southSide, northSide))
        autocompleteDestination?.setLocationBias(RectangularBounds.newInstance(southSide, northSide))
    }



    override fun onResume() {
        super.onResume() // ABRIMOS LA PANTALLA ACTUAL
    }

    override fun onDestroy() { // CIERRA APLICACION O PASAMOS A OTRA ACTIVITY
        super.onDestroy()
        easyWayLocation?.endUpdates()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap?.uiSettings?.isZoomControlsEnabled = true
        onCameraMove()
//        easyWayLocation?.startLocation();

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            return
        }
        googleMap?.isMyLocationEnabled = true
        googleMap?.moveCamera(CameraUpdateFactory.newCameraPosition(
            CameraPosition.builder().target(LatLng(-12.597971084874588, -69.2013902547617)).zoom(14f).build()
        ))

        try {
            val success = googleMap?.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(this, R.raw.style)
            )
            if (!success!!) {
                Log.d("MAPAS", "No se pudo encontrar el estilo")
            }

        } catch (e: Resources.NotFoundException) {
            Log.d("MAPAS", "Error: ${e.toString()}")
        }

    }

    override fun locationOn() {
    }

    override fun currentLocation(location: Location) {
        myLocationLatLng = LatLng(location.latitude, location.longitude)

        if (myLocationMarker == null) {
            val customMarkerSize = 124
            val bitmap = BitmapFactory.decodeResource(resources, R.drawable.my_location_green)
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, customMarkerSize, customMarkerSize, false)
            val customMarkerIcon = BitmapDescriptorFactory.fromBitmap(resizedBitmap)

            val markerOptions = MarkerOptions()
                .position(myLocationLatLng as LatLng)
                .icon(customMarkerIcon)
                .anchor(0.5f, 0.5f)

            myLocationMarker = googleMap?.addMarker(markerOptions)
            binding.btnRoutes.visibility = View.VISIBLE

            if (!isLocationEnabled) {
                val active = TinyDB(this)
                if(active.getString(Constants.ACTIVE) == Constants.ACTIVE_NOTIFICATION){

                    if(!isActiveService(TrashCarService::class.java)){
                        startService(Intent(this, TrashCarService::class.java))
                    }

                    binding.btnNotificationOn.visibility = View.GONE
                    binding.btnNotificationOff.visibility = View.VISIBLE
                }else{
                    binding.btnNotificationOff.visibility = View.GONE
                    binding.btnNotificationOn.visibility = View.VISIBLE
                }
                isLocationEnabled = true
                googleMap?.moveCamera(CameraUpdateFactory.newCameraPosition(
                    CameraPosition.builder().target(myLocationLatLng!!).zoom(17f).build()
                ))
                getNearbyDrivers()
                limitSearch()
                val localId = TinyDB(this).getString(Constants.LOCAL_DB_MICROROUTE)
                if(localId != ""){
                    drawPolylineWithMarkers(localId,googleMap!!)
                }
            }
        } else {
            myLocationMarker?.position = myLocationLatLng!!
        }
    }


    override fun locationCancelled() {

    }

    private fun showEnableGpsDialog(context: Context) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Activar GPS")
            .setMessage("El GPS está desactivado. ¿Desea activarlo?")
            .setPositiveButton("Sí") { dialog, _ ->
                // Abre la pantalla de configuración del GPS
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                context.startActivity(intent)
                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    @SuppressLint("ServiceCast")
    private fun isGpsEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }
    private fun drawPolylineWithMarkers(id: String, map: GoogleMap) {
        val db: FirebaseFirestore = FirebaseFirestore.getInstance()
        db.collection("microroutes").document(id).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val value = snapshot.toObject(Microroutes::class.java)!!
                val coords = value.positions
                //map.clear()
                // Agrega marcador en la primera posición
                if (coords.isNotEmpty()) {
                    val firstPosition = coords.first()
                    map.addMarker(
                        MarkerOptions()
                            .position(LatLng(firstPosition.lat!!, firstPosition.lng!!))
                            .title("Inicio")
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                    )
                }

                // Agrega marcador en la última posición
                if (coords.isNotEmpty()) {
                    val lastPosition = coords.last()
                    map.addMarker(
                        MarkerOptions()
                            .position(LatLng(lastPosition.lat!!, lastPosition.lng!!))
                            .title("Fin")
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                    )
                }

                // Agrega polilínea
                val polylineOptions = PolylineOptions()
                for (coordenada in coords) {
                    polylineOptions.add(LatLng(coordenada.lat!!, coordenada.lng!!))
                }
                polylineOptions.color(Color.RED)
                polylineOptions.width(16f)
                //
                map.addPolyline(polylineOptions)

            }
        }
    }

    private fun isActiveService(myService : Class<TrashCarService>) : Boolean{

        val manager: ActivityManager = getSystemService(Context.ACTIVITY_SERVICE
        )as ActivityManager

        for(service : ActivityManager.RunningServiceInfo in
        manager.getRunningServices(Integer.MAX_VALUE)){
            if(myService.name.equals(service.service.className)){
                return true
            }
        }
        return false
    }


}