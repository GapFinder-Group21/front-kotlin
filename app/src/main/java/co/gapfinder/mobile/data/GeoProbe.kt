package co.gapfinder.mobile.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** Resultado de pedir permiso de ubicación (equivalente a LocationPermission de geolocator). */
enum class GeoAccess { Denied, DeniedForever, WhileInUse, Always }

/** Estado del GPS del sistema. */
enum class GpsSwitch { On, Off }

/** Todo lo relacionado con GPS y permisos de ubicación (antes LocationService + Geolocator). */
object GeoProbe {
    private const val TAG = "GeoProbe"
    private lateinit var appContext: Context
    private var activity: ComponentActivity? = null
    private var launcher: ActivityResultLauncher<Array<String>>? = null
    private var pending: CompletableDeferred<Map<String, Boolean>>? = null

    private val perms = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    /** Debe llamarse en onCreate de la Activity (antes de STARTED). */
    fun bind(host: ComponentActivity) {
        appContext = host.applicationContext
        activity = host
        launcher = host.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            pending?.complete(result)
            pending = null
        }
    }

    fun unbind(host: ComponentActivity) {
        if (activity === host) {
            activity = null
            launcher = null
        }
    }

    fun gpsSwitchedOn(): Boolean {
        val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun granted(): Boolean = perms.any {
        ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
    }

    /** GPS encendido + permiso concedido. */
    fun isReady(): Boolean {
        val on = gpsSwitchedOn()
        Log.d(TAG, "GPS Sensor Switch is: ${if (on) "ON" else "OFF"}")
        if (!on) return false
        val ok = granted()
        Log.d(TAG, "App Permission granted: $ok")
        return ok
    }

    /** Muestra el diálogo del sistema para pedir el permiso de ubicación. */
    suspend fun askPermission(): GeoAccess {
        if (granted()) return GeoAccess.WhileInUse
        val host = activity ?: return GeoAccess.Denied
        val waiter = CompletableDeferred<Map<String, Boolean>>()
        pending = waiter
        launcher?.launch(perms) ?: return GeoAccess.Denied
        val result = waiter.await()
        if (result.values.any { it }) return GeoAccess.WhileInUse
        val canAskAgain = perms.any { host.shouldShowRequestPermissionRationale(it) }
        return if (canAskAgain) GeoAccess.Denied else GeoAccess.DeniedForever
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", appContext.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
    }

    fun openLocationSettings() {
        appContext.startActivity(
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Posición actual con alta precisión (máx. 10 s). Lanza excepción si no se obtiene. */
    @SuppressLint("MissingPermission")
    suspend fun currentFix(timeoutMs: Long = 10_000): Location {
        val client = LocationServices.getFusedLocationProviderClient(appContext)
        val token = CancellationTokenSource()
        return try {
            withTimeout(timeoutMs) {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token).await()
                    ?: throw IllegalStateException("Location unavailable")
            }
        } finally {
            token.cancel()
        }
    }

    /** Emite cada vez que el usuario enciende/apaga el GPS. */
    fun gpsChanges(): Flow<GpsSwitch> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(if (gpsSwitchedOn()) GpsSwitch.On else GpsSwitch.Off)
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION),
            ContextCompat.RECEIVER_EXPORTED,
        )
        awaitClose { appContext.unregisterReceiver(receiver) }
    }
}
