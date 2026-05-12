package com.ppeapp

/**
 * Utility class to maintain consistency of object IDs across video frames.
 */
class Tracker {

    private val tracks = mutableListOf<Detection>()
    private var nextId = 1

    /**
     * Updates the track IDs for a new set of detections.
     * Respects existing track IDs (assigned by the intelligent detector) to avoid overwriting associations.
     */
    fun update(detections: List<Detection>): List<Detection> {
        for (det in detections) {
            // If the detector already assigned an intelligent ID (binding PPE to Person), respect it
            if (det.trackId != -1) continue

            var bestIoU = 0f
            var bestTrack: Detection? = null

            for (track in tracks) {
                val iou = computeIoU(det, track)
                if (iou > bestIoU) {
                    bestIoU = iou
                    bestTrack = track
                }
            }

            if (bestIoU > 0.3f && bestTrack != null) {
                det.trackId = bestTrack.trackId
            } else {
                det.trackId = nextId++
            }
        }

        tracks.clear()
        tracks.addAll(detections)

        return detections
    }

    private fun computeIoU(a: Detection, b: Detection): Float {
        val x1 = maxOf(a.x1, b.x1)
        val y1 = maxOf(a.y1, b.y1)
        val x2 = minOf(a.x2, b.x2)
        val y2 = minOf(a.y2, b.y2)

        val intersection = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
        val areaA = (a.x2 - a.x1) * (a.y2 - a.y1)
        val areaB = (b.x2 - b.x1) * (b.y2 - b.y1)
        val union = areaA + areaB - intersection

        return if (union <= 0f) 0f
        else intersection / union
    }
}
