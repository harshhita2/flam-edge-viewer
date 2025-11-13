package com.example.edgeviewer

import android.content.Context
import android.opengl.GLSurfaceView

class MyGLSurfaceView(context: Context) : GLSurfaceView(context) {
    val renderer: GLRenderer

    init {
        setEGLContextClientVersion(2)
        renderer = GLRenderer()
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }
}
