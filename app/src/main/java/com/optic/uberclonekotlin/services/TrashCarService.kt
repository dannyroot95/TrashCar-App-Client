package com.optic.uberclonekotlin.services

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.graphics.BitmapFactory
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.easywaylocation.EasyWayLocation
import com.example.easywaylocation.Listener
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.GeoPoint
import com.optic.uberclonekotlin.R
import com.optic.uberclonekotlin.activities.MapActivity
import com.optic.uberclonekotlin.providers.AuthProvider
import com.optic.uberclonekotlin.providers.GeoProvider
import org.imperiumlabs.geofirestore.callbacks.GeoQueryEventListener
import kotlin.math.pow

class TrashCarService : Service(),Listener {

    private var isServiceRunning = false
    private var authProvider = AuthProvider()
    private var geoProvider = GeoProvider()
    private var easyWayLocation: EasyWayLocation? = null
    private var myLocationLatLng: LatLng? = null
    private val NOTIFICATION_ID = 12

    private val locationRequest = LocationRequest.create().apply {
        interval = 0
        fastestInterval = 0
        priority = Priority.PRIORITY_HIGH_ACCURACY
        smallestDisplacement = 1f
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (!isServiceRunning) {
            easyWayLocation = EasyWayLocation(this, locationRequest, false, false, this)
            // Marcar el servicio como en ejecución
            easyWayLocation?.endUpdates() // OTROS HILOS DE EJECUCION
            easyWayLocation?.startLocation()
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)
            isServiceRunning = true
        }

        // Indica que el servicio no debe reiniciarse automáticamente
        return START_NOT_STICKY
    }

    override fun locationOn() {
    }

    override fun currentLocation(location: Location) {
        myLocationLatLng = LatLng(location.latitude, location.longitude)
        getNearbyDrivers(myLocationLatLng!!)
    }

    override fun locationCancelled() {
    }


    private fun getNearbyDrivers(myLocationLatLng : LatLng) {

        geoProvider.getNearbyDrivers(myLocationLatLng, 30.0).addGeoQueryEventListener(object:
            GeoQueryEventListener {

            @RequiresApi(Build.VERSION_CODES.O)
            override fun onKeyEntered(documentID: String, location: GeoPoint) {
                val driverLatLng = LatLng(location.latitude,location.longitude)
                if (isNearby(myLocationLatLng, driverLatLng, 1.5)) {
                    sendNotification()
                    //sendNotification("Conductor cercano", "Hay un conductor cerca de ti.")
                }
            }

            override fun onKeyExited(documentID: String) {
            }

            @RequiresApi(Build.VERSION_CODES.O)
            override fun onKeyMoved(documentID: String, location: GeoPoint) {
                val driverLatLng = LatLng(location.latitude,location.longitude)
                if (isNearby(myLocationLatLng, driverLatLng, 1.5)) {
                    sendNotification()
                    Log.d("ISNEAR", "CERCA")
                    //sendNotification("Conductor cercano", "Hay un conductor cerca de ti.")
                }else{
                    Log.d("ISNEAR", "LEJOS")
                }
            }

            override fun onGeoQueryError(exception: Exception) {
            }

            override fun onGeoQueryReady() {

            }

        })
    }

    private fun isNearby(currentPosition: LatLng, targetPosition: LatLng, radius: Double): Boolean {
        val distance = haversine(
            currentPosition.latitude, currentPosition.longitude,
            targetPosition.latitude, targetPosition.longitude
        )
        Log.d("NEAR", distance.toString())
        return distance <= radius
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = toRadians(lat2 - lat1)
        val dLon = toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2)
            .pow(2) + Math.cos(toRadians(lat1)) * Math.cos(toRadians(lat2)) * Math.sin(dLon / 2).pow(2)
        val c = 2 * Math.asin(Math.sqrt(a))
        return 6371.0 * c // Earth radius in kilometers
    }

    private fun toRadians(deg: Double): Double {
        return deg * Math.PI / 180.0
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun createNotification(): Notification {
        val channelId = "YourChannelId"
        val channelName = "YourChannelName"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(notificationChannel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Notificacion activada")
            .setContentText("Obteniendo datos de ubicación")
            .setSmallIcon(R.drawable.uber_car)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        easyWayLocation?.endUpdates()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun sendNotification() {
        val channelId = "YourChannelId"  // Asegúrate de usar el mismo ID de canal
        val notificationId = 22 // Genera un ID único

        val notificationIntent = Intent(applicationContext, MapActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(applicationContext, 0, notificationIntent, 0)

        val notification = Notification.Builder(applicationContext, channelId)
            .setContentTitle("¡Alerta de basurero!")
            .setContentText("Un carro basurero está cerca de tu ubicación.")
            .setSmallIcon(R.drawable.icon_pin)
            .setPriority(Notification.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        // Utiliza NotificationManager para mostrar la notificación
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }


}