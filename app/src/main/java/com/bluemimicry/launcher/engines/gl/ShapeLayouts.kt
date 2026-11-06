package com.bluemimicry.launcher.engines.gl

import com.bluemimicry.launcher.data.Shape3D
import kotlin.math.*

class V3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = V3(x * s, y * s, z * s)
    fun dot(o: V3) = x * o.x + y * o.y + z * o.z
    fun cross(o: V3) = V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun len() = sqrt(dot(this))
    fun norm(): V3 { val l = len().takeIf { it > 1e-6f } ?: 1f; return V3(x / l, y / l, z / l) }
}

/** One icon placement: centre, outward normal, "up" direction and half edge length (model space). */
class Slot(val p: V3, val n: V3, val up: V3, val half: Float)

sealed interface BodySpec {
    data class Cube(val half: Float) : BodySpec
    data class Sphere(val r: Float) : BodySpec
    data class Cylinder(val r: Float, val halfH: Float) : BodySpec
    data class Pyramid(val baseHalf: Float, val height: Float) : BodySpec
    data object None : BodySpec
}

class Layout(val slots: List<Slot>, val body: BodySpec, val radius: Float)

private fun tangentUp(n: V3): V3 {
    val worldUp = V3(0f, 1f, 0f)
    val t = worldUp - n * worldUp.dot(n)
    return if (t.len() < 0.05f) V3(0f, 0f, -1f).let { it - n * it.dot(n) }.norm() else t.norm()
}

object ShapeLayouts {
    fun build(shape: Shape3D, n: Int): Layout = when (shape) {
        Shape3D.CUBE -> cube(n)
        Shape3D.SPHERE -> sphere(n)
        Shape3D.CYLINDER -> cylinder(n)
        Shape3D.RING -> ring(n)
        Shape3D.PYRAMID -> pyramid(n)
        Shape3D.CAROUSEL -> carousel(n)
    }

    private fun cube(n: Int): Layout {
        val g = ceil(sqrt(n / 6f)).toInt().coerceAtLeast(1)
        val cell = 2f / g
        val half = cell * 0.38f
        val faces = listOf(
            V3(0f, 0f, 1f) to V3(0f, 1f, 0f), V3(0f, 0f, -1f) to V3(0f, 1f, 0f),
            V3(1f, 0f, 0f) to V3(0f, 1f, 0f), V3(-1f, 0f, 0f) to V3(0f, 1f, 0f),
            V3(0f, 1f, 0f) to V3(0f, 0f, -1f), V3(0f, -1f, 0f) to V3(0f, 0f, 1f),
        )
        val out = ArrayList<Slot>()
        loop@ for ((nrm, up) in faces) {
            val right = up.cross(nrm)
            for (r in 0 until g) for (c in 0 until g) {
                if (out.size >= n) break@loop
                val u = -1f + cell * (c + 0.5f); val v = 1f - cell * (r + 0.5f)
                out += Slot(nrm * 1.012f + right * u + up * v, nrm, up, half)
            }
        }
        return Layout(out, BodySpec.Cube(0.99f), 1.8f)
    }

    private fun sphere(n: Int): Layout {
        val count = max(n, 1)
        val half = sqrt(4f * PI.toFloat() / count) * 0.40f
        val ga = PI.toFloat() * (3f - sqrt(5f))
        val out = (0 until count).map { i ->
            val y = if (count == 1) 0f else 1f - 2f * i / (count - 1f)
            val r = sqrt((1f - y * y).coerceAtLeast(0f))
            val th = ga * i
            val nrm = V3(cos(th) * r, y, sin(th) * r)
            Slot(nrm * 1.012f, nrm, tangentUp(nrm), half)
        }
        return Layout(out, BodySpec.Sphere(0.99f), 1.3f)
    }

    private fun cylinder(n: Int): Layout {
        val perRow = 9
        val half = 0.2f; val pitch = half * 2.35f
        val rows = ceil(n / perRow.toFloat()).toInt().coerceAtLeast(1)
        val r = perRow * pitch / (2f * PI.toFloat())
        val out = ArrayList<Slot>()
        for (i in 0 until n) {
            val row = i / perRow; val col = i % perRow
            val th = col * 2f * PI.toFloat() / perRow + (row % 2) * PI.toFloat() / perRow
            val nrm = V3(cos(th), 0f, sin(th))
            val y = (rows - 1) / 2f * pitch - row * pitch
            out += Slot(nrm * (r + 0.012f) + V3(0f, y, 0f), nrm, V3(0f, 1f, 0f), half)
        }
        return Layout(out, BodySpec.Cylinder(r * 0.985f, rows * pitch / 2f), max(r, rows * pitch / 2f) * 1.4f)
    }

    private fun ring(n: Int): Layout {
        val half = 0.2f
        val r = max(1.1f, n * half * 2.4f / (2f * PI.toFloat()))
        val out = (0 until n).map { i ->
            val th = i * 2f * PI.toFloat() / max(n, 1)
            val nrm = V3(cos(th), 0f, sin(th))
            Slot(nrm * r, nrm, V3(0f, 1f, 0f), half)
        }
        return Layout(out, BodySpec.None, r * 1.2f)
    }

    private fun carousel(n: Int): Layout {
        val layers = 3
        val half = 0.2f
        val out = ArrayList<Slot>()
        val per = ceil(n / layers.toFloat()).toInt().coerceAtLeast(1)
        for (i in 0 until n) {
            val layer = i / per; val idx = i % per
            val cnt = min(per, n - layer * per)
            val r = max(0.9f + layer * 0.35f, cnt * half * 2.4f / (2f * PI.toFloat()))
            val th = idx * 2f * PI.toFloat() / cnt + layer * 0.5f
            val nrm = V3(cos(th), 0f, sin(th))
            out += Slot(nrm * r + V3(0f, (layer - 1) * 0.55f, 0f), nrm, V3(0f, 1f, 0f), half)
        }
        return Layout(out, BodySpec.None, 2.2f)
    }

    private fun pyramid(n: Int): Layout {
        val bh = 1.3f; val h = 2.0f
        val apex = V3(0f, h / 2f, 0f)
        val corners = listOf(V3(bh, -h / 2f, bh), V3(bh, -h / 2f, -bh), V3(-bh, -h / 2f, -bh), V3(-bh, -h / 2f, bh))
        val perFace = ceil(n / 4f).toInt().coerceAtLeast(1)
        var rows = 1
        while (rows * (rows + 1) / 2 < perFace) rows++
        val out = ArrayList<Slot>()
        for (f in 0 until 4) {
            val b = corners[f]; val c = corners[(f + 1) % 4]
            val nrm = (c - apex).cross(b - apex).norm().let { if (it.dot(apex - V3(0f, 0f, 0f) + (b + c) * 0.5f) < 0f) it * -1f else it }
            val mid = (b + c) * 0.5f
            val up = (apex - mid).norm()
            val edge = (c - b).len()
            val half = edge / rows * 0.32f
            var placed = 0
            for (r in 0 until rows) for (k in 0..r) {
                if (out.size >= n || placed >= perFace) continue
                val t = (r + 0.5f) / rows
                val left = apex + (b - apex) * t; val right = apex + (c - apex) * t
                val p = left + (right - left) * ((k + 0.5f) / (r + 1f))
                out += Slot(p + nrm * 0.02f, nrm, up, half); placed++
            }
        }
        return Layout(out, BodySpec.Pyramid(bh * 0.985f, h), 2.4f)
    }
}
