# PPE Android Application

This project is an Android application designed to detect Personal Protective Equipment (PPE) in real-time using on-device machine learning. The application identifies workers and verifies if they are wearing required safety gear like hardhats and safety vests.

## Features

- Real-time PPE Detection: Process live camera feeds to identify safety compliance.
- Image Upload: Analyze static images from the device gallery.
- Dual-Pass Inference: Uses a high-performance detection strategy that crops persons for high-accuracy PPE verification.
- Object Tracking: Maintains consistent IDs for persons across frames.
- JSON Results: View detailed technical detection data in an expandable console.
- Performance Optimized: Utilizes background threads, NNAPI hardware acceleration, and smart frame-skipping to maintain high FPS.

## FPS Optimizations

- NNAPI Acceleration: Automatically enables Android Neural Networks API for hardware-accelerated inference.
- Adaptive Inference Delay: Dynamic thresholding for frame processing (200ms) to ensure smooth UI.
- Selective Processing: Limits PPE verification to the top 3 largest detected persons per frame to prevent bottlenecks in crowds.
- Background Processing: All AI logic runs on dedicated background threads using Kotlin Coroutines and SingleThreadExecutors.

## Technical Stack

- Language: Kotlin
- UI Framework: Jetpack Compose
- Machine Learning Runtime: ONNX Runtime for Android (v1.17.0)
- Model: YOLO (You Only Look Once) based object detection
- Camera API: Android CameraX
- Concurrency: Kotlin Coroutines

## Project Structure

- MainActivity.kt: Manages the application lifecycle, UI state, and camera integration.
- ONNXDetector.kt: Handles ONNX model initialization, NNAPI configuration, and inference logic.
- Tracker.kt: Implements an IoU-based tracking algorithm for object consistency.
- Detection.kt: Data class representing a single detected object and its properties.

## Setup

1. Ensure you have the ONNX model file named 'best.onnx' in the 'app/src/main/assets/' directory.
2. Build the project using Android Studio.
3. Grant camera permissions when prompted on the device.
