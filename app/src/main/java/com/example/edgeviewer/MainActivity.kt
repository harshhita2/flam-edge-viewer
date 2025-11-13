package com.example.edgeviewer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var cameraController: CameraController
    private lateinit var glSurface: MyGLSurfaceView

    external fun processFrameNV21(nv21: ByteArray, width: Int, height: Int): ByteArray

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
        }

        // Create OpenGL surface
        glSurface = MyGLSurfaceView(this)
        setContentView(glSurface)   // <-- IMPORTANT FIX

        // Initialize camera with callback
        cameraController = CameraController(this) { nv21, w, h ->

            val rgba = processFrameNV21(nv21, w, h)

            glSurface.renderer.updateFrame(rgba, w, h)

            glSurface.requestRender()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::cameraController.isInitialized)
            cameraController.start()
    }

    override fun onPause() {
        super.onPause()
        if (::cameraController.isInitialized)
            cameraController.stop()
    }

    // FIX: Handle permission properly
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            cameraController.start()
        }
    }

    companion object {
        init {
            System.loadLibrary("edgeviewer")
        }
    }
}
