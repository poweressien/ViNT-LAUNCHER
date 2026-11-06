package com.bluemimicry.launcher.engines.gl

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import com.bluemimicry.launcher.data.Shape3D
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs

/** Everything the renderer needs from settings. Replaced atomically from the UI thread. */
data class GlConfig(
    val shape: Shape3D = Shape3D.CUBE,
    val fov: Float = 50f,
    val zoom: Float = 1f,
    val autoRotate: Boolean = true,
    val speed: Float = 1f,
    val light: Float = 1f,
    val metallic: Float = 0.3f,
    val glass: Boolean = false,
    val bodyColor: Int = 0xFFDADCE2.toInt(),
    val bg: Int = 0,
    val fpsCap: Int = 60,
    val sphereDetail: Int = 20,
    val paused: Boolean = false,
)

class ShapeRenderer : GLSurfaceView.Renderer {
    @Volatile var cfg = GlConfig()
    @Volatile var yaw = 25f
    @Volatile var pitch = 18f
    @Volatile var yawVel = 0f
    @Volatile var pitchVel = 0f
    @Volatile var dragging = false
    @Volatile var zoomUser = 1f

    @Volatile private var pendingAtlas: Bitmap? = null
    @Volatile private var pendingCount = 0
    private var count = 0
    @Volatile var layoutDirty = true

    // screen-space hit data (written on GL thread each frame, read on UI thread)
    @Volatile var hitX = FloatArray(0); @Volatile var hitY = FloatArray(0)
    @Volatile var hitR = FloatArray(0); @Volatile var hitVis = BooleanArray(0)

    private var w = 1; private var h = 1
    private val proj = FloatArray(16); private val mv = FloatArray(16); private val mvp = FloatArray(16)
    private val rot = FloatArray(16); private val tmp = FloatArray(16)
    private var progBody = 0; private var progIcon = 0; private var tex = 0
    private var builtShape: Shape3D? = null; private var builtDetail = -1
    private var layout: Layout? = null
    private var body: Mesh? = null
    private var iconV: java.nio.FloatBuffer? = null; private var iconI: java.nio.ShortBuffer? = null; private var iconIdx = 0
    private var lastNs = 0L

    fun setAtlas(b: Bitmap, appCount: Int) { pendingAtlas = b; pendingCount = appCount; layoutDirty = true }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        progBody = program(BODY_VS, BODY_FS); progIcon = program(ICON_VS, ICON_FS)
        val t = IntArray(1); GLES20.glGenTextures(1, t, 0); tex = t[0]
        GLES20.glEnable(GLES20.GL_DEPTH_TEST); GLES20.glEnable(GLES20.GL_CULL_FACE)
        builtShape = null; layoutDirty = true
        pendingAtlas?.let { uploadAtlas(it) }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        w = width; h = height; GLES20.glViewport(0, 0, width, height)
    }

    private fun uploadAtlas(b: Bitmap) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, b, 0)
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    private fun rebuild(c: GlConfig) {
        val l = ShapeLayouts.build(c.shape, count)
        layout = l
        body = Meshes.build(l.body, c.sphereDetail)
        val n = l.slots.size
        val v = FloatArray(n * 4 * 5); val ix = ShortArray(n * 6)
        val cell = 1f / ATLAS_GRID; val eps = 0.0025f
        for ((i, s) in l.slots.withIndex()) {
            val right = s.up.cross(s.n).norm(); val up = s.n.cross(right).norm()
            val r = right * s.half; val u = up * s.half
            val u0 = (i % ATLAS_GRID) * cell + eps; val v0 = (i / ATLAS_GRID) * cell + eps; val u1 = u0 + cell - 2 * eps; val v1 = v0 + cell - 2 * eps
            val pts = arrayOf(s.p - r - u, s.p + r - u, s.p + r + u, s.p - r + u)       // BL BR TR TL
            val uv = arrayOf(u0 to v1, u1 to v1, u1 to v0, u0 to v0)
            for (k in 0 until 4) {
                val o = (i * 4 + k) * 5
                v[o] = pts[k].x; v[o + 1] = pts[k].y; v[o + 2] = pts[k].z; v[o + 3] = uv[k].first; v[o + 4] = uv[k].second
            }
            val b = (i * 4).toShort()
            ix[i * 6] = b; ix[i * 6 + 1] = (b + 1).toShort(); ix[i * 6 + 2] = (b + 2).toShort()
            ix[i * 6 + 3] = b; ix[i * 6 + 4] = (b + 2).toShort(); ix[i * 6 + 5] = (b + 3).toShort()
        }
        iconV = floatBuf(v); iconI = shortBuf(ix); iconIdx = ix.size
        hitX = FloatArray(n); hitY = FloatArray(n); hitR = FloatArray(n); hitVis = BooleanArray(n)
        builtShape = c.shape; builtDetail = c.sphereDetail; layoutDirty = false
    }

    override fun onDrawFrame(gl: GL10?) {
        val c = cfg
        val now = System.nanoTime()
        val minDt = 1_000_000_000L / c.fpsCap.coerceAtLeast(15)
        if (lastNs != 0L && now - lastNs < minDt) {
            try { Thread.sleep(((minDt - (now - lastNs)) / 1_000_000L).coerceAtLeast(1)) } catch (_: InterruptedException) {}
        }
        val t = System.nanoTime()
        val dt = if (lastNs == 0L) 0.016f else ((t - lastNs) / 1e9f).coerceAtMost(0.05f)
        lastNs = t

        pendingAtlas?.let { uploadAtlas(it); count = pendingCount; pendingAtlas = null; layoutDirty = true }
        if (layoutDirty || builtShape != c.shape || builtDetail != c.sphereDetail) rebuild(c)

        // motion: drag inertia + optional auto-rotate
        if (!dragging) {
            yaw += yawVel * dt; pitch = (pitch + pitchVel * dt).coerceIn(-80f, 80f)
            val decay = Math.pow(0.04, dt.toDouble()).toFloat(); yawVel *= decay; pitchVel *= decay
            if (c.autoRotate && !c.paused && abs(yawVel) < 8f) yaw += 22f * c.speed * dt
        }

        val bgc = when (c.bg) { 1 -> floatArrayOf(0.05f, 0.06f, 0.09f); 2 -> floatArrayOf(0.35f, 0.55f, 0.78f); else -> floatArrayOf(0.82f, 0.82f, 0.82f) }
        GLES20.glClearColor(bgc[0], bgc[1], bgc[2], 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val l = layout ?: return
        if (count == 0) return

        val aspect = w.toFloat() / h
        Matrix.perspectiveM(proj, 0, c.fov, aspect, 0.1f, 60f)
        val dist = l.radius * 1.9f / (c.zoom * zoomUser).coerceIn(0.4f, 3f) / kotlin.math.tan(Math.toRadians(c.fov / 2.0)).toFloat() * 0.55f
        Matrix.setIdentityM(rot, 0)
        Matrix.rotateM(rot, 0, pitch, 1f, 0f, 0f); Matrix.rotateM(rot, 0, yaw, 0f, 1f, 0f)
        Matrix.setIdentityM(tmp, 0); Matrix.translateM(tmp, 0, 0f, 0f, -dist)
        Matrix.multiplyMM(mv, 0, tmp, 0, rot, 0)
        Matrix.multiplyMM(mvp, 0, proj, 0, mv, 0)

        val glass = c.glass
        val col = floatArrayOf(((c.bodyColor shr 16) and 255) / 255f, ((c.bodyColor shr 8) and 255) / 255f, (c.bodyColor and 255) / 255f, if (glass) 0.32f else 1f)

        if (!glass) drawBody(col, c)
        drawIcons(c, l)
        if (glass) { GLES20.glEnable(GLES20.GL_BLEND); GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA); GLES20.glDepthMask(false); drawBody(col, c); GLES20.glDepthMask(true); GLES20.glDisable(GLES20.GL_BLEND) }
        updateHits(l)
    }

    private fun drawBody(col: FloatArray, c: GlConfig) {
        val m = body ?: return
        GLES20.glUseProgram(progBody)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(progBody, "uMVP"), 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(progBody, "uRot"), 1, false, rot, 0)
        GLES20.glUniform4fv(GLES20.glGetUniformLocation(progBody, "uColor"), 1, col, 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(progBody, "uLight"), c.light)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(progBody, "uMetal"), c.metallic)
        val aPos = GLES20.glGetAttribLocation(progBody, "aPos"); val aN = GLES20.glGetAttribLocation(progBody, "aNrm")
        m.verts.position(0); GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 24, m.verts); GLES20.glEnableVertexAttribArray(aPos)
        m.verts.position(3); GLES20.glVertexAttribPointer(aN, 3, GLES20.GL_FLOAT, false, 24, m.verts); GLES20.glEnableVertexAttribArray(aN)
        m.idx.position(0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, m.count, GLES20.GL_UNSIGNED_SHORT, m.idx)
        GLES20.glDisableVertexAttribArray(aPos); GLES20.glDisableVertexAttribArray(aN)
    }

    private fun drawIcons(c: GlConfig, l: Layout) {
        val v = iconV ?: return; val ix = iconI ?: return
        GLES20.glUseProgram(progIcon)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(progIcon, "uTex"), 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(progIcon, "uMVP"), 1, false, mvp, 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(progIcon, "uLight"), c.light.coerceIn(0.6f, 1.2f))
        val aPos = GLES20.glGetAttribLocation(progIcon, "aPos"); val aUv = GLES20.glGetAttribLocation(progIcon, "aUV")
        v.position(0); GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 20, v); GLES20.glEnableVertexAttribArray(aPos)
        v.position(3); GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, 20, v); GLES20.glEnableVertexAttribArray(aUv)
        ix.position(0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, iconIdx, GLES20.GL_UNSIGNED_SHORT, ix)
        GLES20.glDisableVertexAttribArray(aPos); GLES20.glDisableVertexAttribArray(aUv)
    }

    private val clip = FloatArray(4); private val src = FloatArray(4)
    private fun updateHits(l: Layout) {
        val n = l.slots.size
        for (i in 0 until n) {
            val s = l.slots[i]
            // facing test in view space (rotation only)
            src[0] = s.n.x; src[1] = s.n.y; src[2] = s.n.z; src[3] = 0f
            Matrix.multiplyMV(clip, 0, rot, 0, src, 0)
            val facing = clip[2] > 0.02f
            src[0] = s.p.x; src[1] = s.p.y; src[2] = s.p.z; src[3] = 1f
            Matrix.multiplyMV(clip, 0, mvp, 0, src, 0)
            if (clip[3] <= 0f) { hitVis[i] = false; continue }
            hitX[i] = (clip[0] / clip[3] * 0.5f + 0.5f) * w
            hitY[i] = (1f - (clip[1] / clip[3] * 0.5f + 0.5f)) * h
            hitR[i] = s.half * proj[5] * h * 0.5f / clip[3]
            hitVis[i] = facing
        }
    }

    /** Nearest front-facing icon to the touch point, or -1. */
    fun pick(x: Float, y: Float, minRadiusPx: Float): Int {
        var best = -1; var bd = Float.MAX_VALUE
        val vis = hitVis; val hx = hitX; val hy = hitY; val hr = hitR
        for (i in vis.indices) {
            if (!vis[i] || i >= count) continue
            val d = Math.hypot((x - hx[i]).toDouble(), (y - hy[i]).toDouble()).toFloat()
            if (d < maxOf(hr[i], minRadiusPx) && d < bd) { bd = d; best = i }
        }
        return best
    }

    private fun program(vs: String, fs: String): Int {
        fun sh(type: Int, src: String) = GLES20.glCreateShader(type).also { GLES20.glShaderSource(it, src); GLES20.glCompileShader(it) }
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, sh(GLES20.GL_VERTEX_SHADER, vs)); GLES20.glAttachShader(p, sh(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glLinkProgram(p); return p
    }

    companion object {
        const val BODY_VS = "uniform mat4 uMVP; uniform mat4 uRot; attribute vec3 aPos; attribute vec3 aNrm; varying vec3 vN;" +
            "void main(){ vN=(uRot*vec4(aNrm,0.0)).xyz; gl_Position=uMVP*vec4(aPos,1.0); }"
        const val BODY_FS = "precision mediump float; varying vec3 vN; uniform vec4 uColor; uniform float uLight; uniform float uMetal;" +
            "void main(){ vec3 n=normalize(vN); vec3 L=normalize(vec3(-0.4,0.7,0.8)); float d=max(dot(n,L),0.0);" +
            "vec3 H=normalize(L+vec3(0.0,0.0,1.0)); float s=pow(max(dot(n,H),0.0),mix(8.0,64.0,uMetal))*uMetal;" +
            "vec3 c=uColor.rgb*(0.42+0.58*d)*uLight+vec3(s); gl_FragColor=vec4(c,uColor.a); }"
        const val ICON_VS = "uniform mat4 uMVP; attribute vec3 aPos; attribute vec2 aUV; varying vec2 vUV;" +
            "void main(){ vUV=aUV; gl_Position=uMVP*vec4(aPos,1.0); }"
        const val ICON_FS = "precision mediump float; varying vec2 vUV; uniform sampler2D uTex; uniform float uLight;" +
            "void main(){ vec4 c=texture2D(uTex,vUV); if(c.a<0.03) discard; gl_FragColor=vec4(c.rgb*uLight,c.a); }"
    }
}
