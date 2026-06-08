package com.example.filter

/**
 * A standard 1-Dimensional Kalman Filter for real-time signal smoothing.
 *
 * @param q Process Noise Covariance: Represents our uncertainty about the internal system process.
 *                 Smaller values indicate high stability (slow, heavy smoothing). Default is 0.05.
 * @param r Measurement Noise Covariance: Represents sensor inaccuracy/jitter.
 *                 Higher values mean the sensor is noisy, so we trust our predictions more than raw measurements. Default is 4.0.
 * @param initialEstimate Initial state estimate (e.g., initial RSSI).
 * @param initialError Initial estimate error covariance.
 */
class KalmanFilter(
    private val q: Double = 0.05,
    private val r: Double = 4.0,
    private var initialEstimate: Double = -100.0,
    private var initialError: Double = 1.0
) {
    private var x: Double = initialEstimate // Estimated state estimate (smoothed value)
    private var p: Double = initialError     // Estimated error covariance

    init {
        reset(initialEstimate)
    }

    /**
     * Updates the status of the Kalman Filter with a new measurement and returns the smoothed value.
     */
    fun update(measurement: Double): Double {
        // Step 1: Prediction Update
        p += q

        // Step 2: Measurement Update (Kalman Gain)
        val k = p / (p + r)

        // Step 3: State Update
        x += k * (measurement - x)

        // Step 4: Error Covariance Update
        p *= (1.0 - k)

        return x
    }

    /**
     * Resets the filter to a known starting state.
     */
    fun reset(value: Double) {
        x = value
        p = initialError
    }

    /**
     * Retrieves the current estimate as a Float.
     */
    fun getCurrentEstimate(): Float {
        return x.toFloat()
    }
}
