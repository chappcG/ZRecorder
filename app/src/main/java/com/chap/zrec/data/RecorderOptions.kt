package com.chap.zrec.data

object RecorderOptions {
    val resolutions = listOf("640x360" to "360p", "1280x720" to "720p", "1920x1080" to "1080p", "2560x1440" to "1440p")
    val frameRates = listOf(24, 30, 60)
    val videoEncoders = listOf("H.264", "H.265")
    val orientations = listOf("Auto", "Portrait", "Landscape")
    val countdowns = listOf(0, 3, 5, 10)
    val audioModes = listOf("None", "Microphone", "Internal Audio", "Internal + Microphone")
    val audioBitrates = listOf(64_000, 128_000, 192_000, 256_000)

    fun resolutionLabel(value: String): String = resolutions.firstOrNull { it.first == value }?.second ?: value
    fun audioBitrateLabel(value: Int): String = "${value / 1000} kbps"
    fun bitrateLabel(value: Float): String = "${trim(value)} Mbps"
    fun trim(value: Float): String = if (value == value.toLong().toFloat()) value.toLong().toString() else value.toString()
}
