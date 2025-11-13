package com.example.edgeviewer

import android.app.Activity
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size

class CameraController(
    private val activity: Activity,
    private val callback: (ByteArray, Int, Int) -> Unit
) {

    private lateinit var cameraDevice: CameraDevice
    private lateinit var imageReader: ImageReader
    private var captureSession: CameraCaptureSession? = null

    // Correct camera size (landscape orientation)
    private val previewSize = Size(640, 480)

    private val cameraManager: CameraManager by lazy {
        activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private val backgroundThread = HandlerThread("CameraThread").apply { start() }
    private val backgroundHandler = Handler(backgroundThread.looper)

    fun start() {
        val cameraId = cameraManager.cameraIdList[0]  // Rear camera

        imageReader = ImageReader.newInstance(
            previewSize.width,
            previewSize.height,
            ImageFormat.YUV_420_888,
            2
        )

        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener

            val nv21 = yuv420ToNV21(image)
            callback(nv21, previewSize.width, previewSize.height)

            image.close()
        }, backgroundHandler)

        cameraManager.openCamera(
            cameraId,
            object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    cameraDevice = device
                    createSession()
                }

                override fun onDisconnected(device: CameraDevice) {
                    device.close()
                }

                override fun onError(device: CameraDevice, error: Int) {
                    device.close()
                }
            },
            backgroundHandler
        )
    }

    private fun createSession() {
        val surface = imageReader.surface

        val request = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surface)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        }

        cameraDevice.createCaptureSession(
            listOf(surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    session.setRepeatingRequest(request.build(), null, backgroundHandler)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e("CameraController", "Session configuration failed")
                }
            },
            backgroundHandler
        )
    }

    fun stop() {
        captureSession?.close()
        imageReader.close()
        cameraDevice.close()
    }

    // ------------------------------
    //   PERFECT YUV420 → NV21 FIX
    // ------------------------------
    private fun yuv420ToNV21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 2

        val nv21 = ByteArray(ySize + uvSize)

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        // ---- COPY Y ----
        val yBuffer = yPlane.buffer
        val yRowStride = yPlane.rowStride

        var pos = 0
        for (row in 0 until height) {
            yBuffer.position(row * yRowStride)
            yBuffer.get(nv21, pos, width)
            pos += width
        }

        // ---- COPY UV ----
        val chromaHeight = height / 2
        val chromaWidth = width / 2

        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride

        for (row in 0 until chromaHeight) {
            for (col in 0 until chromaWidth) {
                val vIdx = row * vRowStride + col * vPixelStride
                val uIdx = row * uRowStride + col * uPixelStride

                nv21[pos++] = vPlane.buffer[vIdx]   // V
                nv21[pos++] = uPlane.buffer[uIdx]   // U
            }
        }

        return nv21
    }
}
