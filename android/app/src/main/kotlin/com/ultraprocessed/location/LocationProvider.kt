package com.ultraprocessed.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Best-effort coarse current location. Returns null if the user hasn't
 * granted any location permission, or if the device can't produce a
 * reading in [timeoutMs] ms - we never block log creation on it.
 *
 * Uses the FusedLocationProviderClient with `getCurrentLocation` (rather
 * than `lastLocation`) so a meal logged immediately after walking
 * indoors still gets a fresh fix, while honouring the timeout.
 */
data class DeviceLocation(val lat: Double, val lng: Double)

object LocationProvider {

    fun hasPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    suspend fun current(context: Context, timeoutMs: Long = 5_000L): DeviceLocation? {
        if (!hasPermission(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(60_000L)
            .setDurationMillis(timeoutMs)
            .build()

        return suspendCancellableCoroutine { cont ->
            try {
                val token = com.google.android.gms.tasks.CancellationTokenSource()
                client.getCurrentLocation(request, token.token)
                    .addOnSuccessListener { loc ->
                        if (loc == null) cont.resume(null)
                        else cont.resume(DeviceLocation(loc.latitude, loc.longitude))
                    }
                    .addOnFailureListener { cont.resume(null) }
                cont.invokeOnCancellation { token.cancel() }
            } catch (_: SecurityException) {
                cont.resume(null)
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun quietLooper(): Looper? = null
}
