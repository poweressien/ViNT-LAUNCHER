package com.bluemimicry.launcher.engines.gl

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * Real OpenGL ES 2.0 surface. One finger rotates (with inertia), two fingers pinch-zoom,
 * a tap launches the front-facing icon under the finger.
 */
@SuppressLint("ViewConstructor")
class ShapeGlView(context: Context, private val onPick: (Int) -> Unit) : GLSurfaceView(context) {
    val renderer = ShapeRenderer()
    private var lastAtlas: Bitmap? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val minPick = resources.displayMetrics.density * 26f
    private var downX = 0f; private var downY = 0f; private var lx = 0f; private var ly = 0f
    private var lt = 0L; private var moved = false; private var multi = false

    private val scale = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            renderer.zoomUser = (renderer.zoomUser * d.scaleFactor).coerceIn(0.4f, 3f); return true
        }
    })

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun setAtlas(b: Bitmap, count: Int) {
        if (b === lastAtlas) return
        lastAtlas = b; renderer.setAtlas(b, count)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        scale.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y; lx = e.x; ly = e.y; lt = e.eventTime; moved = false; multi = false
                renderer.dragging = true; renderer.yawVel = 0f; renderer.pitchVel = 0f
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_POINTER_DOWN -> multi = true
            MotionEvent.ACTION_MOVE -> if (!multi && e.pointerCount == 1) {
                val dx = e.x - lx; val dy = e.y - ly
                if (abs(e.x - downX) > slop || abs(e.y - downY) > slop) moved = true
                renderer.yaw += dx * 0.45f; renderer.pitch = (renderer.pitch + dy * 0.45f).coerceIn(-80f, 80f)
                val dtS = ((e.eventTime - lt) / 1000f).coerceAtLeast(0.001f)
                renderer.yawVel = dx * 0.45f / dtS; renderer.pitchVel = dy * 0.45f / dtS
                lx = e.x; ly = e.y; lt = e.eventTime
            }
            MotionEvent.ACTION_UP -> {
                renderer.dragging = false
                if (!moved && !multi) { val i = renderer.pick(e.x, e.y, minPick); if (i >= 0) post { onPick(i) } }
                renderer.yawVel = renderer.yawVel.coerceIn(-400f, 400f); renderer.pitchVel = renderer.pitchVel.coerceIn(-300f, 300f)
            }
            MotionEvent.ACTION_CANCEL -> renderer.dragging = false
        }
        return true
    }
}
