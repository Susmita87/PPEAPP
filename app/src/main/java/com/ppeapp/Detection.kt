package com.ppeapp

data class Detection(
    val classId: Int,
    val className: String,
    val confidence: Float,

    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,

    var trackId: Int = -1
)