package com.jamalsquad.jamalify.playback

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Mendeteksi gerakan shake pada perangkat untuk memperpanjang sleep timer.
 * Menggunakan accelerometer untuk mendeteksi percepatan mendadak.
 */
class ShakeDetector(private val context: Context) : SensorEventListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _shakeEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val shakeEvent: SharedFlow<Unit> = _shakeEvent.asSharedFlow()

    private var lastShakeTime = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var lastUpdate = 0L

    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdate < 100) return

        val diffTime = currentTime - lastUpdate
        lastUpdate = currentTime

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val speed = sqrt(
            ((x - lastX) * (x - lastX) +
             (y - lastY) * (y - lastY) +
             (z - lastZ) * (z - lastZ)).toDouble()
        ) / diffTime * 10000

        if (speed > SHAKE_THRESHOLD && currentTime - lastShakeTime > SHAKE_COOLDOWN_MS) {
            lastShakeTime = currentTime
            scope.launch {
                _shakeEvent.emit(Unit)
            }
        }

        lastX = x
        lastY = y
        lastZ = z
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        private const val SHAKE_THRESHOLD = 800f
        private const val SHAKE_COOLDOWN_MS = 2000L
    }
}