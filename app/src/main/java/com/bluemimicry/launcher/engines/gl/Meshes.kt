package com.bluemimicry.launcher.engines.gl

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.*

fun floatBuf(a: FloatArray): FloatBuffer =
    ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(a); position(0) }

fun shortBuf(a: ShortArray): ShortBuffer =
    ByteBuffer.allocateDirect(a.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().apply { put(a); position(0) }

/** Interleaved pos(3)+normal(3) body mesh. */
class Mesh(val verts: FloatBuffer, val idx: ShortBuffer, val count: Int)

private class MB {
    val v = ArrayList<Float>(); val i = ArrayList<Short>()
    fun vert(p: V3, n: V3): Short { v += listOf(p.x, p.y, p.z, n.x, n.y, n.z); return (v.size / 6 - 1).toShort() }
    fun tri(a: Short, b: Short, c: Short) { i += a; i += b; i += c }
    fun build() = Mesh(floatBuf(v.toFloatArray()), shortBuf(i.toShortArray()), i.size)
}

object Meshes {
    fun build(spec: BodySpec, detail: Int): Mesh? = when (spec) {
        is BodySpec.Cube -> cube(spec.half)
        is BodySpec.Sphere -> sphere(spec.r, detail)
        is BodySpec.Cylinder -> cylinder(spec.r, spec.halfH, detail * 2)
        is BodySpec.Pyramid -> pyramid(spec.baseHalf, spec.height)
        BodySpec.None -> null
    }

    private fun quad(b: MB, a: V3, c: V3, d: V3, e: V3, n: V3) {
        val i0 = b.vert(a, n); val i1 = b.vert(c, n); val i2 = b.vert(d, n); val i3 = b.vert(e, n)
        b.tri(i0, i1, i2); b.tri(i0, i2, i3)
    }

    private fun cube(h: Float): Mesh {
        val b = MB()
        val ax = listOf(V3(0f, 0f, 1f), V3(0f, 0f, -1f), V3(1f, 0f, 0f), V3(-1f, 0f, 0f), V3(0f, 1f, 0f), V3(0f, -1f, 0f))
        val ups = listOf(V3(0f, 1f, 0f), V3(0f, 1f, 0f), V3(0f, 1f, 0f), V3(0f, 1f, 0f), V3(0f, 0f, -1f), V3(0f, 0f, 1f))
        for (k in 0 until 6) {
            val n = ax[k]; val up = ups[k]; val r = up.cross(n)
            val c = n * h
            quad(b, c - r * h - up * h, c + r * h - up * h, c + r * h + up * h, c - r * h + up * h, n)
        }
        return b.build()
    }

    private fun sphere(r: Float, detail: Int): Mesh {
        val b = MB()
        val st = detail.coerceAtLeast(8); val sl = st * 2
        for (i in 0..st) {
            val phi = PI.toFloat() * i / st
            for (j in 0..sl) {
                val th = 2f * PI.toFloat() * j / sl
                val n = V3(sin(phi) * cos(th), cos(phi), sin(phi) * sin(th))
                b.vert(n * r, n)
            }
        }
        for (i in 0 until st) for (j in 0 until sl) {
            val a = (i * (sl + 1) + j).toShort(); val c = (a + sl + 1).toShort()
            b.tri(a, (a + 1).toShort(), c); b.tri((a + 1).toShort(), (c + 1).toShort(), c)
        }
        return b.build()
    }

    private fun cylinder(r: Float, hh: Float, seg: Int): Mesh {
        val b = MB()
        val s = seg.coerceAtLeast(16)
        for (j in 0..s) {
            val th = 2f * PI.toFloat() * j / s
            val n = V3(cos(th), 0f, sin(th))
            b.vert(V3(n.x * r, -hh, n.z * r), n); b.vert(V3(n.x * r, hh, n.z * r), n)
        }
        for (j in 0 until s) {
            val a = (j * 2).toShort()
            b.tri(a, (a + 1).toShort(), (a + 2).toShort()); b.tri((a + 1).toShort(), (a + 3).toShort(), (a + 2).toShort())
        }
        for (sign in listOf(1f, -1f)) {
            val n = V3(0f, sign, 0f)
            val c = b.vert(V3(0f, sign * hh, 0f), n)
            val first = (b.v.size / 6).toShort()
            for (j in 0..s) { val th = 2f * PI.toFloat() * j / s; b.vert(V3(cos(th) * r, sign * hh, sin(th) * r), n) }
            for (j in 0 until s) { val a = (first + j).toShort(); if (sign > 0) b.tri(c, (a + 1).toShort(), a) else b.tri(c, a, (a + 1).toShort()) }
        }
        return b.build()
    }

    private fun pyramid(bh: Float, h: Float): Mesh {
        val b = MB()
        val apex = V3(0f, h / 2f, 0f)
        val cs = listOf(V3(bh, -h / 2f, bh), V3(bh, -h / 2f, -bh), V3(-bh, -h / 2f, -bh), V3(-bh, -h / 2f, bh))
        for (f in 0 until 4) {
            val p = cs[f]; val q = cs[(f + 1) % 4]
            var n = (q - apex).cross(p - apex).norm()
            if (n.dot((p + q) * 0.5f) < 0f) n = n * -1f
            val a = b.vert(apex, n); val c = b.vert(p, n); val d = b.vert(q, n)
            // wind counter-clockwise as seen from outside
            if ((p - apex).cross(q - apex).dot(n) > 0f) b.tri(a, c, d) else b.tri(a, d, c)
        }
        val n = V3(0f, -1f, 0f)
        val i0 = b.vert(cs[0], n); val i1 = b.vert(cs[1], n); val i2 = b.vert(cs[2], n); val i3 = b.vert(cs[3], n)
        b.tri(i0, i1, i2); b.tri(i0, i2, i3)
        return b.build()
    }
}
