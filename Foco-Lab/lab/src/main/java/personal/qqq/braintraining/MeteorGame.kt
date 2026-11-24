package personal.qqq.braintraining

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.*
import kotlin.random.Random

/**
 * Dodged meteors needed per level. Each level raises fall speed and spawn rate; from level 3
 * meteors can come in pairs.
 */
const val METEORS_PER_LEVEL = 8
const val METEOR_LIVES = 3

private class Rock(
    var lane: Int,
    var y: Float,
    val radius: Float,
    var angle: Float,
    val spin: Float,
    val seed: Int,
    val crystal: Boolean,
)

/**
 * All mutable game data lives here and is advanced once per display frame (60/120 Hz) without
 * recomposing the screen.
 */
private class MeteorWorld {
    val random = Random(System.nanoTime())
    val rocks = mutableListOf<Rock>()
    val particles = Particles(random)
    val stars =
        List(3) { layer ->
            List(26 - layer * 6) { Offset(random.nextFloat(), random.nextFloat()) }.toMutableList()
        }
    var width = 0f
    var height = 0f
    var shipX = Float.NaN
    var lane = 1
    var tilt = 0f
    var skin: Color? = null
    var spawnIn = .6f
    var invulnerable = 0f
    var shake = 0f
    var flash = 0f
    var time = 0f

    fun laneX(l: Int) = width * (l + .5f) / 3f

    val shipY
        get() = height * .84f

    val shipSize
        get() = width * .085f
}

class MeteorStats {
    var dodged by mutableIntStateOf(0)
    var score by mutableIntStateOf(0)
    var lives by mutableIntStateOf(METEOR_LIVES)
    var level by mutableIntStateOf(1)
    var crystals by mutableIntStateOf(0)
}

private fun speedFor(level: Int) =
    (.42f + .085f * (level - 1)).coerceAtMost(1.35f) // screen heights per second

private fun spawnFor(level: Int) =
    (1.05f - .07f * (level - 1)).coerceAtLeast(.36f) // seconds between waves

@Composable
fun MeteorBoard(
    running: Boolean,
    stats: MeteorStats,
    lane: Int,
    setLane: (Int) -> Unit,
    modifier: Modifier = Modifier,
    skin: Color? = null,
) {
    val world = remember { MeteorWorld() }
    world.skin = skin
    val feedback = LocalFeedback.current
    val motion = LocalMotionEnabled.current
    var frame by remember { mutableLongStateOf(0L) }
    val currentLane by rememberUpdatedState(lane)
    var banner by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(banner) {
        if (banner != null) {
            kotlinx.coroutines.delay(1500)
            banner = null
        }
    }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = 0L
        while (stats.lives > 0) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(.05f)
                last = now
                step(
                    world,
                    stats,
                    currentLane,
                    dt,
                    motion,
                    onHit = { feedback(FeedbackCue.RETRY) },
                    onDodge = { feedback(FeedbackCue.TAP) },
                    onCrystal = { feedback(FeedbackCue.CORRECT) },
                    onLevel = {
                        banner = it
                        feedback(FeedbackCue.LEVEL_UP)
                    },
                )
                frame = now
            }
        }
    }

    val swipe = with(LocalDensity.current) { 40.dp.toPx() }
    Box(modifier.clip(RoundedCornerShape(28.dp))) {
        Canvas(
            Modifier.fillMaxSize()
                .semantics {
                    contentDescription =
                        "Ruta espacial: carril ${lane + 1} de 3, ${stats.lives} vidas"
                }
                .pointerInput(running) {
                    if (running)
                        detectTapGestures { p ->
                            setLane(
                                if (p.x < size.width / 2f) (currentLane - 1).coerceAtLeast(0)
                                else (currentLane + 1).coerceAtMost(2)
                            )
                        }
                }
                .pointerInput(running) {
                    if (running) {
                        var total = 0f
                        detectHorizontalDragGestures(onDragStart = { total = 0f }) { _, d ->
                            total += d
                            if (total > swipe) {
                                setLane((currentLane + 1).coerceAtMost(2))
                                total = 0f
                            } else if (total < -swipe) {
                                setLane((currentLane - 1).coerceAtLeast(0))
                                total = 0f
                            }
                        }
                    }
                }
        ) {
            frame // read so every frame redraws
            world.width = size.width
            world.height = size.height
            if (world.shipX.isNaN()) world.shipX = world.laneX(world.lane)
            val shakeOffset =
                if (world.shake > 0f)
                    Offset(
                        sin(world.time * 90f) * world.shake * 14f,
                        cos(world.time * 70f) * world.shake * 10f,
                    )
                else Offset.Zero
            drawRect(
                Brush.verticalGradient(
                    listOf(Color(0xFF04060F), Color(0xFF0B1030), Color(0xFF1A1145))
                )
            )
            translate(shakeOffset.x, shakeOffset.y) { drawWorld(world, stats.level) }
            if (world.flash > 0f) drawRect(Color(0xFFFF4D6A).copy(alpha = .38f * world.flash))
        }
        // HUD
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(METEOR_LIVES) { i ->
                Icon(
                    if (i < stats.lives) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    null,
                    Modifier.size(22.dp),
                    tint =
                        if (i < stats.lives) Color(0xFFFF5C7A) else Color.White.copy(alpha = .35f),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "NIVEL ${stats.level}",
                Modifier.clip(CircleShape)
                    .background(Color.White.copy(alpha = .12f))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Text(
            "${stats.score}",
            Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
        )
        // Level-up banner
        AnimatedVisibility(
            banner != null,
            Modifier.align(Alignment.Center),
            enter =
                if (motion) scaleIn(spring(.5f, 400f), initialScale = .4f) + fadeIn()
                else fadeIn(snap()),
            exit = fadeOut(tween(if (motion) 300 else 0)),
        ) {
            val l = banner ?: stats.level
            Column(
                Modifier.clip(RoundedCornerShape(22.dp))
                    .background(Color(0xCC0B1030))
                    .padding(horizontal = 26.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "¡NIVEL $l!",
                    color = Color(0xFF91FFCE),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "Velocidad ×${"%.1f".format(speedFor(l) / speedFor(1))}",
                    color = Color.White.copy(alpha = .8f),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        if (!running && stats.lives > 0)
            Box(
                Modifier.fillMaxSize().background(Color(0x99000000)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Pause, null, Modifier.size(64.dp), tint = Color.White)
            }
    }
}

private fun step(
    w: MeteorWorld,
    s: MeteorStats,
    lane: Int,
    dt: Float,
    motion: Boolean,
    onHit: () -> Unit,
    onDodge: () -> Unit,
    onCrystal: () -> Unit,
    onLevel: (Int) -> Unit,
) {
    if (w.width == 0f || dt == 0f) return
    w.time += dt
    w.lane = lane
    val speed = speedFor(s.level) * w.height
    // Ship glides to its lane and leans into the turn.
    val target = w.laneX(lane)
    val before = w.shipX
    w.shipX += (target - w.shipX) * min(1f, dt * 16f)
    w.tilt = (((w.shipX - before) / dt) / w.width * 9f).coerceIn(-22f, 22f)
    w.invulnerable = max(0f, w.invulnerable - dt)
    w.shake = max(0f, w.shake - dt * 2.5f)
    w.flash = max(0f, w.flash - dt * 3f)
    // Parallax stars: farther layers move slower.
    w.stars.forEachIndexed { layer, list ->
        val v = (.05f + layer * .09f) * speedFor(s.level) / .42f
        for (i in list.indices) {
            var p = list[i]
            p = Offset(p.x, p.y + v * dt)
            if (p.y > 1f) p = Offset(w.random.nextFloat(), 0f)
            list[i] = p
        }
    }
    // Spawn waves; never block all three lanes.
    w.spawnIn -= dt
    if (w.spawnIn <= 0f) {
        w.spawnIn = spawnFor(s.level) * (.8f + w.random.nextFloat() * .4f)
        val lanes = (0..2).shuffled(w.random)
        val count =
            if (s.level >= 3 && w.random.nextFloat() < min(.6f, .15f * (s.level - 2))) 2 else 1
        for (i in 0 until count) w.rocks +=
            Rock(
                lanes[i],
                -w.width * .1f,
                w.width * (.06f + w.random.nextFloat() * .035f),
                w.random.nextFloat() * 360f,
                (w.random.nextFloat() - .5f) * 240f,
                w.random.nextInt(),
                false,
            )
        if (w.random.nextFloat() < .16f)
            w.rocks += Rock(lanes[2], -w.width * .35f, w.width * .045f, 0f, 90f, 0, true)
    }
    val iter = w.rocks.iterator()
    while (iter.hasNext()) {
        val r = iter.next()
        r.y += speed * dt * (if (r.crystal) .85f else 1f)
        r.angle += r.spin * dt
        val dx = w.laneX(r.lane) - w.shipX
        val dy = r.y - w.shipY
        val hitRange = r.radius + w.shipSize * .45f
        if (dx * dx + dy * dy < hitRange * hitRange) {
            if (r.crystal) {
                s.crystals++
                s.score += 25 * s.level
                burst(w, w.laneX(r.lane), r.y, Color(0xFF7CF5FF), 14, motion)
                onCrystal()
                iter.remove()
                continue
            }
            if (w.invulnerable == 0f) {
                s.lives--
                w.invulnerable = 1.6f
                w.shake = 1f
                w.flash = 1f
                burst(w, w.laneX(r.lane), r.y, Color(0xFFFF8A3D), 26, motion)
                burst(w, w.shipX, w.shipY, Color(0xFFFFE08A), 12, motion)
                onHit()
                iter.remove()
                continue
            }
        }
        if (r.y - r.radius > w.height) {
            iter.remove()
            if (!r.crystal) {
                s.dodged++
                s.score += 10 * s.level
                onDodge()
                val next = 1 + s.dodged / METEORS_PER_LEVEL
                if (next > s.level) {
                    s.level = next
                    onLevel(next)
                }
            }
        }
    }
    // Engine exhaust + particle physics.
    if (motion && w.random.nextFloat() < dt * 60f)
        w.particles.emit(
            w.shipX + (w.random.nextFloat() - .5f) * w.shipSize * .3f,
            w.shipY + w.shipSize * .7f,
            (w.random.nextFloat() - .5f) * 30f,
            160f + w.random.nextFloat() * 90f,
            .35f,
            Color(0xFF7DF9FF),
            2.5f + w.random.nextFloat() * 2f,
            motion,
        )
    w.particles.step(dt)
}

private fun burst(w: MeteorWorld, x: Float, y: Float, color: Color, count: Int, motion: Boolean) =
    w.particles.burst(x, y, color, count, motion)

private fun DrawScope.drawWorld(w: MeteorWorld, level: Int) {
    // Stars
    w.stars.forEachIndexed { layer, list ->
        list.forEach { p ->
            drawCircle(
                Color.White.copy(alpha = .25f + layer * .25f),
                1f + layer * 1.1f,
                Offset(p.x * size.width, p.y * size.height),
            )
        }
    }
    // Lane guides
    for (i in 1..2) {
        val x = size.width * i / 3f
        drawLine(
            Color.White.copy(alpha = .05f),
            Offset(x, 0f),
            Offset(x, size.height),
            2f,
            pathEffect =
                PathEffect.dashPathEffect(
                    floatArrayOf(18f, 22f),
                    -w.time * speedFor(level) * size.height % 40f,
                ),
        )
    }
    // Rocks
    w.rocks.forEach { r ->
        val x = w.laneX(r.lane)
        if (r.crystal) drawCrystal(Offset(x, r.y), r.radius, w.time)
        else drawMeteor(Offset(x, r.y), r)
    }
    // Sparks
    with(w.particles) { draw() }
    // Ship (blinks while invulnerable)
    if (w.invulnerable == 0f || (w.time * 12f).toInt() % 2 == 0)
        drawShip(Offset(w.shipX, w.shipY), w.shipSize, w.tilt, w.time, w.skin)
}

private fun DrawScope.drawMeteor(c: Offset, r: Rock) {
    // Glowing trail behind the falling rock.
    drawRect(
        Brush.verticalGradient(
            listOf(Color.Transparent, Color(0xFFFF7A3C).copy(alpha = .45f)),
            c.y - r.radius * 5f,
            c.y,
        ),
        Offset(c.x - r.radius * .55f, c.y - r.radius * 5f),
        androidx.compose.ui.geometry.Size(r.radius * 1.1f, r.radius * 5f),
    )
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFFFF8A3D).copy(alpha = .35f), Color.Transparent),
            c,
            r.radius * 1.8f,
        ),
        r.radius * 1.8f,
        c,
    )
    rotate(r.angle, c) {
        val rnd = Random(r.seed)
        val path = Path()
        for (i in 0 until 10) {
            val a = i / 10f * 6.283f
            val k = r.radius * (.82f + rnd.nextFloat() * .22f)
            if (i == 0) path.moveTo(c.x + cos(a) * k, c.y + sin(a) * k)
            else path.lineTo(c.x + cos(a) * k, c.y + sin(a) * k)
        }
        path.close()
        drawPath(
            path,
            Brush.radialGradient(
                listOf(Color(0xFFFFC27A), Color(0xFFD9602B), Color(0xFF6B2A1E)),
                c - Offset(r.radius * .3f, r.radius * .3f),
                r.radius * 1.3f,
            ),
        )
        drawCircle(Color(0x55401812), r.radius * .22f, c + Offset(r.radius * .3f, r.radius * .15f))
        drawCircle(
            Color(0x55401812),
            r.radius * .14f,
            c + Offset(-r.radius * .25f, r.radius * .35f),
        )
        drawCircle(Color(0x44401812), r.radius * .1f, c + Offset(-r.radius * .1f, -r.radius * .4f))
    }
}

private fun DrawScope.drawCrystal(c: Offset, r: Float, time: Float) {
    val pulse = 1f + sin(time * 8f) * .12f
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFF7CF5FF).copy(alpha = .5f), Color.Transparent),
            c,
            r * 2.6f * pulse,
        ),
        r * 2.6f * pulse,
        c,
    )
    rotate(time * 60f, c) {
        val p =
            Path().apply {
                moveTo(c.x, c.y - r * 1.3f)
                lineTo(c.x + r, c.y)
                lineTo(c.x, c.y + r * 1.3f)
                lineTo(c.x - r, c.y)
                close()
            }
        drawPath(
            p,
            Brush.linearGradient(
                listOf(Color(0xFFE6FFFF), Color(0xFF38D9F5), Color(0xFF2A6CF0)),
                c - Offset(r, r),
                c + Offset(r, r),
            ),
        )
        drawLine(
            Color.White.copy(alpha = .7f),
            Offset(c.x - r * .5f, c.y),
            Offset(c.x + r * .5f, c.y),
            2f,
        )
    }
}

private fun DrawScope.drawShip(c: Offset, s: Float, tilt: Float, time: Float, skin: Color? = null) {
    rotate(tilt, c) {
        // Flame flickers every frame.
        val flame = s * (.75f + sin(time * 40f) * .12f + cos(time * 23f) * .08f)
        drawPath(
            Path().apply {
                moveTo(c.x - s * .22f, c.y + s * .55f)
                quadraticTo(c.x, c.y + s * .55f + flame * 1.4f, c.x + s * .22f, c.y + s * .55f)
                close()
            },
            Brush.verticalGradient(
                listOf(Color(0xFFFFFFFF), Color(0xFF7DF9FF), Color(0x002A6CF0)),
                c.y + s * .5f,
                c.y + s * .55f + flame * 1.4f,
            ),
        )
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFF7DF9FF).copy(alpha = .3f), Color.Transparent),
                c,
                s * 1.6f,
            ),
            s * 1.6f,
            c,
        )
        // Fins
        // Lote C: a rocket skin from a chest only recolors the fins.
        val fin =
            Brush.verticalGradient(
                if (skin != null)
                    listOf(
                        skin,
                        skin.copy(
                            red = skin.red * .6f,
                            green = skin.green * .6f,
                            blue = skin.blue * .6f,
                        ),
                    )
                else listOf(Color(0xFFFF5C7A), Color(0xFFB8234A)),
                c.y,
                c.y + s * .7f,
            )
        drawPath(
            Path().apply {
                moveTo(c.x - s * .28f, c.y)
                lineTo(c.x - s * .72f, c.y + s * .62f)
                lineTo(c.x - s * .25f, c.y + s * .5f)
                close()
            },
            fin,
        )
        drawPath(
            Path().apply {
                moveTo(c.x + s * .28f, c.y)
                lineTo(c.x + s * .72f, c.y + s * .62f)
                lineTo(c.x + s * .25f, c.y + s * .5f)
                close()
            },
            fin,
        )
        // Hull
        val hull =
            Path().apply {
                moveTo(c.x, c.y - s)
                cubicTo(
                    c.x + s * .42f,
                    c.y - s * .55f,
                    c.x + s * .38f,
                    c.y + s * .2f,
                    c.x + s * .26f,
                    c.y + s * .58f,
                )
                lineTo(c.x - s * .26f, c.y + s * .58f)
                cubicTo(c.x - s * .38f, c.y + s * .2f, c.x - s * .42f, c.y - s * .55f, c.x, c.y - s)
                close()
            }
        drawPath(
            hull,
            Brush.horizontalGradient(
                listOf(Color(0xFF9AA8C7), Color(0xFFF4F7FF), Color(0xFFB7C3DD), Color(0xFF6B7899)),
                c.x - s * .4f,
                c.x + s * .4f,
            ),
        )
        drawPath(hull, Color(0xFF3A4566), style = Stroke(s * .04f))
        // Cockpit
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFFCFFFF0), Color(0xFF1EDB90), Color(0xFF0B5E45)),
                c + Offset(-s * .05f, -s * .4f),
                s * .2f,
            ),
            s * .16f,
            c + Offset(0f, -s * .35f),
        )
        drawLine(
            Color(0xFFFF5C7A),
            Offset(c.x - s * .2f, c.y + s * .15f),
            Offset(c.x + s * .2f, c.y + s * .15f),
            s * .06f,
        )
    }
}
