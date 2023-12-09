package com.optic.uberclonekotlin.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.optic.uberclonekotlin.databinding.ActivityMainBinding
import com.optic.uberclonekotlin.providers.AuthProvider
import com.optic.uberclonekotlin.providers.ClientProvider
import com.optic.uberclonekotlin.providers.DriverProvider

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    val authProvider = AuthProvider()
    val driverProvider = DriverProvider()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        if(!isGpsEnabled(this)){
            showEnableGpsDialog(this)
        }

        binding.btnRegister.setOnClickListener { goToRegister() }
        binding.btnLogin.setOnClickListener { login() }
    }

    private fun login() {
        val email = binding.textFieldEmail.text.toString()
        val password = binding.textFieldPassword.text.toString()

        if (isValidForm(email, password)) {
            binding.progressCircular.visibility = View.VISIBLE

            authProvider.login(email, password).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = task.result.user
                    val userId = user?.uid
                    if (!userId.isNullOrBlank()) {
                        driverProvider.getDriver(userId).addOnSuccessListener { dr ->
                            if (dr.exists()) {
                                authProvider.logout()
                                Toast.makeText(
                                    this@MainActivity,
                                    "Usuario no permitido!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                binding.progressCircular.visibility = View.GONE
                            } else {
                                binding.progressCircular.visibility = View.GONE
                                goToMap()
                            }
                        }
                    } else {
                        binding.progressCircular.visibility = View.GONE
                        Toast.makeText(
                            this@MainActivity,
                            "Error iniciando sesión",
                            Toast.LENGTH_SHORT
                        ).show()
                        Log.d("FIREBASE", "ERROR: El ID del usuario es nulo o vacío")
                    }
                } else {
                    binding.progressCircular.visibility = View.GONE
                    Toast.makeText(
                        this@MainActivity,
                        "Error iniciando sesión",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.d("FIREBASE", "ERROR: ${task.exception.toString()}")
                }
            }
        }
    }

    private fun goToMap() {
        val i = Intent(this, MapActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(i)
    }
    
    private fun isValidForm(email: String, password: String): Boolean {
        
        if (email.isEmpty()) {
            Toast.makeText(this, "Ingresa tu correo electronico", Toast.LENGTH_SHORT).show()
            return false
        }
        
        if (password.isEmpty()) {
            Toast.makeText(this, "Ingresa tu contraseña", Toast.LENGTH_SHORT).show()
            return false
        }
        
        return true
    }

    private fun goToRegister() {
        val i = Intent(this, RegisterActivity::class.java)
        startActivity(i)
    }

    override fun onStart() {
        super.onStart()
        if (authProvider.existSession()) {
            goToMap()
        }
    }

    @SuppressLint("ServiceCast")
    private fun isGpsEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
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


}