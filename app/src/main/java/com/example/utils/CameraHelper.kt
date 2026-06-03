package com.example.utils

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraHelper(private val context: Context) {

    private var imageCapture: ImageCapture? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var isBound = false
    private var isCapturing = false

    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider? = null,
        onInitialized: () -> Unit = {}
    ) {
        if (isBound) {
            Log.d("CameraHelper", "Camera already bound. Skipping rebinding.")
            onInitialized()
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build()
                if (surfaceProvider != null) {
                    preview.setSurfaceProvider(surfaceProvider)
                }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                
                isBound = true
                onInitialized()
                Log.d("CameraHelper", "Camera successfully bound to lifecycle.")
            } catch (e: Exception) {
                Log.e("CameraHelper", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun takePhoto(
        onSuccess: (filePath: String) -> Unit,
        onError: (exception: Exception) -> Unit
    ) {
        if (isCapturing) {
            val err = Exception("A photo capture is already in progress")
            Log.w("CameraHelper", "Capture ignored: capture already in progress")
            onError(err)
            return
        }

        val capture = imageCapture ?: run {
            val err = Exception("Camera not bound or initialized")
            Log.e("CameraHelper", "Capture failed: Camera not bound", err)
            onError(err)
            return
        }

        isCapturing = true

        val outputDir = File(context.filesDir, "intruders").apply {
            if (!exists()) {
                mkdirs()
            }
        }
        val photoFile = File(outputDir, "intruder_${System.currentTimeMillis()}.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    isCapturing = false
                    val path = photoFile.absolutePath
                    Log.d("CameraHelper", "Photo save success: $path")
                    onSuccess(path)
                }

                override fun onError(exception: ImageCaptureException) {
                    isCapturing = false
                    Log.e("CameraHelper", "Photo capture failed: ${exception.message}", exception)
                    onError(exception)
                }
            }
        )
    }

    fun shutdown() {
        cameraExecutor.shutdown()
    }
}
