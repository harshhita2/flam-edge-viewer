package com.example.edgeviewer

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GLRenderer : GLSurfaceView.Renderer {
    private val TAG = "GLRenderer"

    private var textureId = -1
    private var programId = -1

    @Volatile private var rgbaData: ByteArray? = null
    @Volatile private var frameWidth = 0
    @Volatile private var frameHeight = 0

    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var texBuffer: FloatBuffer

    private val vertexShaderSrc = """
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = aPosition;
            vTexCoord = aTexCoord;
        }
    """

    private val fragmentShaderSrc = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D uTexture;
        void main() {
            gl_FragColor = texture2D(uTexture, vTexCoord);
        }
    """

    fun updateFrame(data: ByteArray, w: Int, h: Int) {
        // Called from camera/background thread — store latest frame
        rgbaData = data
        frameWidth = w
        frameHeight = h
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)

        // Compile + link shaders
        val vs = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderSrc)
        val fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSrc)
        programId = GLES20.glCreateProgram()
        GLES20.glAttachShader(programId, vs)
        GLES20.glAttachShader(programId, fs)
        GLES20.glLinkProgram(programId)

        // Check link status
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Log.e(TAG, "Program link failed: ${GLES20.glGetProgramInfoLog(programId)}")
            GLES20.glDeleteProgram(programId)
            programId = -1
            return
        }

        GLES20.glUseProgram(programId)

        // Fullscreen quad
        val vertices = floatArrayOf(
            -1f, -1f,
            1f, -1f,
            -1f,  1f,
            1f,  1f
        )

        val texCoords = floatArrayOf(
            1f, 1f,
            1f, 0f,
            0f, 1f,
            0f, 0f
        )


        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        vertexBuffer.put(vertices).position(0)

        texBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        texBuffer.put(texCoords).position(0)

        // Create texture
        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        textureId = texIds[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        // Ensure unpack alignment is 1 for tightly packed rows
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val data = rgbaData ?: return
        val w = frameWidth
        val h = frameHeight
        if (w <= 0 || h <= 0) return
        if (programId == -1) return

        // Upload new texture data (use direct buffer)
        val buffer = ByteBuffer.allocateDirect(data.size).order(ByteOrder.nativeOrder())
        buffer.put(data).position(0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

        // Upload pixels
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            w,
            h,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            buffer
        )

        // Use program
        GLES20.glUseProgram(programId)

        // Set texture uniform to unit 0
        val uTexLoc = GLES20.glGetUniformLocation(programId, "uTexture")
        GLES20.glUniform1i(uTexLoc, 0)

        // Set up attributes (position + texcoord)
        val aPos = GLES20.glGetAttribLocation(programId, "aPosition")
        val aTex = GLES20.glGetAttribLocation(programId, "aTexCoord")

        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        texBuffer.position(0)
        GLES20.glEnableVertexAttribArray(aTex)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, texBuffer)

        // Draw full quad
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        // Disable attributes
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aTex)
    }

    private fun loadShader(type: Int, code: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, code)
        GLES20.glCompileShader(shader)

        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            Log.e(TAG, "Shader compile failed: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }
}
