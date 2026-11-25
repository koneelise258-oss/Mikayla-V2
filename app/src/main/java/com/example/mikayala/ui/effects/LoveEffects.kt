package com.example.mikayala.ui.effects

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Petites animations du couple : cœurs, papillons, étoiles, paillettes et baskets, en violet et vert.
 * - « ambiance » : de temps en temps (toutes les 1 à 3 minutes), 3 à 5 formes montent doucement ;
 * - « éclat » : réponse à un geste (double appui = ❤️, message d'amour, victoire dans un jeu).
 * Couche transparente au-dessus de l'appli : elle ne capte aucun toucher.
 */
object LoveEffects {
    enum class Kind { HEART, BUTTERFLY, STAR, SPARKLE, SHOE }
    enum class Theme { LOVE, CELEBRATION, MIXED }

    data class Burst(val theme: Theme, val x: Float? = null, val y: Float? = null, val count: Int = 16)

    private val _bursts = MutableSharedFlow<Burst>(extraBufferCapacity = 8)
    val bursts = _bursts.asSharedFlow()

    private const val PREFS = "mikayala_prefs"
    private const val KEY = "love_effects_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, enabled).apply()
    }

    /** Éclat d'animation ; x/y en pixels écran (null = bas-centre). */
    fun burst(theme: Theme = Theme.LOVE, x: Float? = null, y: Float? = null, count: Int = 16) {
        _bursts.tryEmit(Burst(theme, x, y, count))
    }

    private val loveWords = Regex(
        "(❤|💜|💚|💕|💖|💗|💘|💞|😍|🥰|😘|je t'?aime|jtm|mon amour|mon cœur|mon coeur|ma chérie|mon chéri|bébé|love you|i love)",
        RegexOption.IGNORE_CASE
    )

    /** Le texte mérite-t-il une pluie de cœurs ? */
    fun isLoveMessage(text: String): Boolean = loveWords.containsMatchIn(text)

    val violets = listOf(Color(0xFF8B5CF6), Color(0xFFA78BFA), Color(0xFFC4B5FD), Color(0xFF7C3AED))
    val greens = listOf(Color(0xFF10B981), Color(0xFF34D399), Color(0xFF6EE7B7), Color(0xFF059669))
}

private class Particle(
    val kind: LoveEffects.Kind,
    val color: Color,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val size: Float,
    val lifeMs: Float,
    val spin: Float,
    val swayPhase: Float,
    val gravity: Float,
    var ageMs: Float = 0f
)

@Composable
fun LoveEffectsLayer(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val particles = remember { ArrayList<Particle>() }
    var frameTick by remember { mutableLongStateOf(0L) }
    // Taille de l'écran (lue hors composition : pas d'état observé)
    val dims = remember { FloatArray(2) }

    // Animations désactivées par le système (accessibilité) ou dans les réglages : rien
    fun allowed(): Boolean {
        val scale = try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        } catch (_: Exception) { 1f }
        return scale > 0f && LoveEffects.isEnabled(context)
    }

    fun pickKind(theme: LoveEffects.Theme): LoveEffects.Kind {
        val r = Random.nextFloat()
        return when (theme) {
            LoveEffects.Theme.LOVE -> if (r < 0.65f) LoveEffects.Kind.HEART else if (r < 0.85f) LoveEffects.Kind.SPARKLE else LoveEffects.Kind.BUTTERFLY
            LoveEffects.Theme.CELEBRATION -> if (r < 0.4f) LoveEffects.Kind.STAR else if (r < 0.65f) LoveEffects.Kind.SPARKLE else if (r < 0.9f) LoveEffects.Kind.BUTTERFLY else LoveEffects.Kind.HEART
            LoveEffects.Theme.MIXED -> when {
                r < 0.35f -> LoveEffects.Kind.HEART
                r < 0.6f -> LoveEffects.Kind.BUTTERFLY
                r < 0.8f -> LoveEffects.Kind.STAR
                r < 0.94f -> LoveEffects.Kind.SPARKLE
                else -> LoveEffects.Kind.SHOE
            }
        }
    }

    fun pickColor(): Color = if (Random.nextBoolean()) LoveEffects.violets.random() else LoveEffects.greens.random()

    fun spawnBurst(b: LoveEffects.Burst) {
        if (dims[0] == 0f || dims[1] == 0f || !allowed()) return
        val ox = b.x ?: (dims[0] / 2f)
        val oy = b.y ?: (dims[1] * 0.78f)
        repeat(b.count.coerceIn(4, 30)) {
            val angle = (-PI / 2 + (Random.nextDouble() - 0.5) * PI * 1.1).toFloat()
            val speed = 260f + Random.nextFloat() * 420f
            particles.add(
                Particle(
                    kind = pickKind(b.theme),
                    color = pickColor(),
                    x = ox, y = oy,
                    vx = cos(angle) * speed, vy = sin(angle) * speed,
                    size = 14f + Random.nextFloat() * 20f,
                    lifeMs = 1300f + Random.nextFloat() * 900f,
                    spin = (Random.nextFloat() - 0.5f) * 160f,
                    swayPhase = Random.nextFloat() * 6.28f,
                    gravity = 260f
                )
            )
        }
    }

    fun spawnAmbient() {
        if (dims[0] == 0f || dims[1] == 0f || !allowed()) return
        val n = 3 + Random.nextInt(3)
        repeat(n) {
            particles.add(
                Particle(
                    kind = pickKind(LoveEffects.Theme.MIXED),
                    color = pickColor().copy(alpha = 0.85f),
                    x = Random.nextFloat() * dims[0],
                    y = dims[1] + 30f + Random.nextFloat() * 120f,
                    vx = (Random.nextFloat() - 0.5f) * 30f,
                    vy = -(70f + Random.nextFloat() * 60f),
                    size = 16f + Random.nextFloat() * 14f,
                    lifeMs = 6500f + Random.nextFloat() * 3000f,
                    spin = (Random.nextFloat() - 0.5f) * 30f,
                    swayPhase = Random.nextFloat() * 6.28f,
                    gravity = 0f
                )
            )
        }
    }

    LaunchedEffect(Unit) {
        launch { LoveEffects.bursts.collect { spawnBurst(it) } }
        // Ambiance : une petite vague peu après l'ouverture, puis rarement
        launch {
            delay(2500)
            spawnAmbient()
            while (true) {
                delay(60_000L + Random.nextLong(120_000L))
                spawnAmbient()
            }
        }
        // Animation : ne tourne que lorsqu'il y a des formes à l'écran
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000f).coerceAtMost(48f)
                last = now
                if (particles.isNotEmpty()) {
                    val s = dt / 1000f
                    for (p in particles) {
                        p.ageMs += dt
                        p.vy += p.gravity * s
                        p.x += (p.vx + sin(p.ageMs / 420f + p.swayPhase) * 22f) * s
                        p.y += p.vy * s
                    }
                    particles.removeAll { it.ageMs >= it.lifeMs }
                    frameTick = now
                } else {
                    last = 0L
                }
            }
            if (particles.isEmpty()) delay(120)
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        dims[0] = size.width
        dims[1] = size.height
        val tick = frameTick // relit à chaque image tant que des formes bougent
        if (tick < 0L) return@Canvas
        for (p in particles) {
            val t = p.ageMs / p.lifeMs
            val alpha = when {
                t < 0.12f -> t / 0.12f
                t > 0.7f -> ((1f - t) / 0.3f).coerceIn(0f, 1f)
                else -> 1f
            }
            val grow = if (t < 0.15f) 0.6f + t / 0.15f * 0.4f else 1f
            drawLoveShape(p.kind, p.color.copy(alpha = p.color.alpha * alpha), Offset(p.x, p.y), p.size * grow, p.spin * p.ageMs / 1000f, p.ageMs)
        }
    }
}

private fun DrawScope.drawLoveShape(kind: LoveEffects.Kind, color: Color, at: Offset, size: Float, rotationDeg: Float, ageMs: Float) {
    translate(at.x, at.y) {
        rotate(rotationDeg, pivot = Offset.Zero) {
            when (kind) {
                LoveEffects.Kind.HEART -> drawPath(heartPath(size), color)
                LoveEffects.Kind.STAR -> drawPath(starPath(size, 5, 0.45f), color)
                LoveEffects.Kind.SPARKLE -> {
                    val tw = 0.75f + 0.25f * sin(ageMs / 120f)
                    scale(tw, tw, pivot = Offset.Zero) { drawPath(starPath(size * 0.9f, 4, 0.28f), color) }
                }
                LoveEffects.Kind.BUTTERFLY -> {
                    // Battement d'ailes : les ailes se referment et s'ouvrent
                    val flap = 0.35f + 0.65f * kotlin.math.abs(sin(ageMs / 110f))
                    scale(flap, 1f, pivot = Offset.Zero) { drawPath(butterflyWings(size), color) }
                    drawRect(color, topLeft = Offset(-size * 0.05f, -size * 0.32f), size = androidx.compose.ui.geometry.Size(size * 0.1f, size * 0.64f))
                }
                LoveEffects.Kind.SHOE -> drawPath(shoePath(size), color)
            }
        }
    }
}

private fun heartPath(s: Float): Path = Path().apply {
    val w = s
    moveTo(0f, w * 0.35f)
    cubicTo(-w * 0.55f, -w * 0.05f, -w * 0.5f, -w * 0.55f, 0f, -w * 0.25f)
    cubicTo(w * 0.5f, -w * 0.55f, w * 0.55f, -w * 0.05f, 0f, w * 0.35f)
    close()
}

private fun starPath(s: Float, points: Int, inner: Float): Path = Path().apply {
    val outerR = s / 2f
    val innerR = outerR * inner
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) outerR else innerR
        val a = -PI / 2 + i * PI / points
        val x = (cos(a) * r).toFloat()
        val y = (sin(a) * r).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun butterflyWings(s: Float): Path = Path().apply {
    val w = s / 2f
    // Ailes du haut
    moveTo(0f, -w * 0.05f)
    cubicTo(-w * 0.5f, -w * 1.0f, -w * 1.15f, -w * 0.7f, -w * 0.95f, -w * 0.1f)
    cubicTo(-w * 0.8f, w * 0.1f, -w * 0.35f, w * 0.05f, 0f, -w * 0.05f)
    moveTo(0f, -w * 0.05f)
    cubicTo(w * 0.5f, -w * 1.0f, w * 1.15f, -w * 0.7f, w * 0.95f, -w * 0.1f)
    cubicTo(w * 0.8f, w * 0.1f, w * 0.35f, w * 0.05f, 0f, -w * 0.05f)
    // Ailes du bas
    moveTo(0f, w * 0.05f)
    cubicTo(-w * 0.45f, w * 0.1f, -w * 0.85f, w * 0.35f, -w * 0.6f, w * 0.7f)
    cubicTo(-w * 0.35f, w * 0.95f, -w * 0.1f, w * 0.5f, 0f, w * 0.05f)
    moveTo(0f, w * 0.05f)
    cubicTo(w * 0.45f, w * 0.1f, w * 0.85f, w * 0.35f, w * 0.6f, w * 0.7f)
    cubicTo(w * 0.35f, w * 0.95f, w * 0.1f, w * 0.5f, 0f, w * 0.05f)
}

/** Petite basket stylisée (semelle + tige arrondie). */
private fun shoePath(s: Float): Path = Path().apply {
    val w = s / 2f
    moveTo(-w, w * 0.35f)
    lineTo(w * 1.05f, w * 0.35f)
    cubicTo(w * 1.1f, w * 0.05f, w * 0.85f, -w * 0.1f, w * 0.45f, -w * 0.15f)
    lineTo(w * 0.05f, -w * 0.55f)
    cubicTo(-w * 0.2f, -w * 0.75f, -w * 0.55f, -w * 0.7f, -w * 0.75f, -w * 0.45f)
    cubicTo(-w * 0.95f, -w * 0.2f, -w * 1.05f, w * 0.05f, -w, w * 0.35f)
    close()
}
