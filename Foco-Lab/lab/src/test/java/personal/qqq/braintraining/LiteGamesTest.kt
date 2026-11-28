package personal.qqq.braintraining

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import personal.qqq.braintraining.core.Ddos

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiteGamesTest {
    private val compose = createAndroidComposeRule<LabActivity>()
    @get:Rule
    val rules: TestRule =
        RuleChain.outerRule(
                object : ExternalResource() {
                    override fun before() {
                        ApplicationProvider.getApplicationContext<Context>()
                            .deleteDatabase("brain-training.db")
                    }
                }
            )
            .around(compose)

    private fun capture(name: String) {
        compose.runOnIdle {
            val v = compose.activity.window.decorView
            val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(b))
            val file = File("build/screenshots/$name")
            file.parentFile?.mkdirs()
            file.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            b.recycle()
        }
    }

    /**
     * Fase 3: Pulso, Parejas matemáticas and Órbitas left the menus; they come back with "Mostrar
     * ejercicios clásicos". Robolectric keeps the previous test's database instance open, so the
     * setting is forced to the wanted value instead of toggled.
     */
    private fun classic(on: Boolean = true) {
        compose.onNodeWithContentDescription("Ajustes").performClick()
        compose.onNodeWithContentDescription("Mostrar ejercicios clásicos").performScrollTo()
        val state = if (on) isOn() else isOff()
        if (
            compose
                .onAllNodes(hasContentDescription("Mostrar ejercicios clásicos").and(state))
                .fetchSemanticsNodes()
                .isEmpty()
        )
            compose.onNodeWithContentDescription("Mostrar ejercicios clásicos").performClick()
        compose.waitUntil(5000) {
            compose
                .onAllNodes(hasContentDescription("Mostrar ejercicios clásicos").and(state))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[BrainViewModel::class.java]
        compose.waitUntil(5000) { vm.showClassic == on }
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.waitForIdle()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("Explora").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithText("A tu manera").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun cards(prefix: String) =
        compose
            .onAllNodes(
                SemanticsMatcher("card $prefix") { node ->
                    node.config.getOrNull(SemanticsProperties.ContentDescription)?.any {
                        it.startsWith(prefix)
                    } == true
                }
            )
            .fetchSemanticsNodes()
            .map { it.config.getOrNull(SemanticsProperties.ContentDescription)!!.first() }

    @Test
    fun infinitePairsMatchAndMoveWithoutRewards() {
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("Cognitive Lab").fetchSemanticsNodes().isNotEmpty()
        }
        capture("lab-inicio.png")
        classic()
        compose.onNodeWithText("Arcade").performScrollTo().performClick()
        compose.onNodeWithText("Infinito").performClick()
        compose.onNodeWithText("Parejas matemáticas").performScrollTo().performClick()
        compose.onNodeWithText("MODO INFINITO").assertExists()
        capture("lab-parejas.png")
        val left = cards("Operación ")
        val right = cards("Resultado ")
        assertEquals(4, left.size)
        assertEquals(4, right.size)
        val parts = left.first().removePrefix("Operación ").split(" ")
        val a = parts[0].toInt()
        val b = parts[2].toInt()
        val answer =
            when (parts[1]) {
                "+" -> a + b
                "−" -> a - b
                "×" -> a * b
                else -> error("Operación desconocida")
            }
        val target = "Resultado $answer"
        assertTrue(target in right)
        compose.onNodeWithContentDescription(left.first()).performClick()
        compose.onNodeWithContentDescription(target).performClick()
        compose.waitUntil(5000) {
            compose
                .onAllNodesWithText("¡Bien! Las cartas cambiaron de lugar.")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        val newLeft = cards("Operación ")
        val newRight = cards("Resultado ")
        assertEquals(3, newLeft.size)
        assertEquals(3, newRight.size)
        newLeft.forEachIndexed { index, card -> assertNotEquals(left.indexOf(card), index) }
        newRight.forEachIndexed { index, card -> assertNotEquals(right.indexOf(card), index) }
        for (remaining in 2 downTo 1) {
            val current = cards("Operación ")
            val expression = current.first()
            val tokens = expression.removePrefix("Operación ").split(" ")
            val x = tokens[0].toInt()
            val y = tokens[2].toInt()
            val result =
                when (tokens[1]) {
                    "+" -> x + y
                    "−" -> x - y
                    "×" -> x * y
                    else -> error("Operación desconocida")
                }
            val survivor = if (remaining == 1) current.last() else null
            val previousY =
                survivor?.let {
                    compose.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot.top
                }
            compose.onNodeWithContentDescription(expression).performClick()
            compose.onNodeWithContentDescription("Resultado $result").performClick()
            compose.waitUntil(5000) { cards("Operación ").size == remaining }
            if (survivor != null) {
                val newY =
                    compose
                        .onNodeWithContentDescription(survivor)
                        .fetchSemanticsNode()
                        .boundsInRoot
                        .top
                assertNotEquals(previousY, newY)
            }
        }
        compose.onNodeWithText("MODO INFINITO").assertExists()
        compose.onAllNodesWithText("XP", substring = true).assertCountEquals(0)
    }

    @Test
    fun arcadeMenuShowsTheFase3Games() {
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("Cognitive Lab").fetchSemanticsNodes().isNotEmpty()
        }
        classic(false)
        compose.onNodeWithText("Arcade").performScrollTo().performClick()
        compose.onNodeWithText("Turno anti-DDoS").assertExists()
        compose.onNodeWithText("Parejas de incidentes").assertExists()
        compose.onAllNodesWithText("Pulso").assertCountEquals(0)
        compose.onAllNodesWithText("Parejas matemáticas").assertCountEquals(0)
        compose.onNodeWithText("Turno anti-DDoS").performClick()
        compose.onNodeWithText("Empezar").performScrollTo().assertExists()
    }

    /**
     * Paso 2 (docs/AUDITORIA-JUEGOS-V3.md): a Turno anti-DDoS partida is 5 chained waves. No
     * "Solución paso a paso" panel between them, the result is saved with its 5 waves and the
     * result screen shows the packets and the points that were saved.
     */
    @Test
    fun ddosPartidaChainsFiveWavesAndShowsWhatItSaves() {
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("Cognitive Lab").fetchSemanticsNodes().isNotEmpty()
        }
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[BrainViewModel::class.java]
        // Animations off: the belt steps packet by packet, so the whole partida can be driven by
        // the test clock.
        compose.onNodeWithContentDescription("Ajustes").performClick()
        compose.onNodeWithContentDescription("Animaciones").performScrollTo()
        if (
            compose
                .onAllNodes(hasContentDescription("Animaciones").and(isOff()))
                .fetchSemanticsNodes()
                .isEmpty()
        )
            compose.onNodeWithContentDescription("Animaciones").performClick()
        compose.waitUntil(5000) {
            compose
                .onAllNodes(hasContentDescription("Animaciones").and(isOff()))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.waitUntil(5000) {
            vm.preferences.value.any { it.key == "motion" && it.value == "false" }
        }
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Arcade").performScrollTo().performClick()
        compose.onNodeWithText("Turno anti-DDoS").performClick()
        compose
            .onNodeWithText("Subes de nivel con el 85 % de los paquetes", substring = true)
            .assertExists()
        compose.onNodeWithText("Empezar").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("OLEADA 1 / 5").assertExists()
        compose.onNodeWithText("Oleada 1/5").assertExists()
        capture("lab-ddos-oleada1.png")
        compose.mainClock.autoAdvance = false
        val waves = linkedSetOf<Int>()
        var steps = 0
        while (vm.run?.phase in listOf(Phase.ANSWER, Phase.FEEDBACK) && steps++ < 700) {
            val r = vm.run!!
            assertNotEquals("no panel between waves", Phase.FEEDBACK, r.phase)
            if (waves.add(r.index)) {
                compose.mainClock.advanceTimeBy(300)
                if (r.index == Ddos.SEAL_WAVE) {
                    compose.onNodeWithText("SELLADO ✓").assertExists()
                    capture("lab-ddos-sello.png")
                }
                assertEquals(r.index, r.challenge.ddos!!.wave)
            }
            // Tap the first malicious packet on the belt, if any: some points and a combo, like a
            // distracted player.
            val t = r.challenge.ddos!!
            val v = Ddos.label(t)
            t.packets.indices
                .firstOrNull {
                    v[it].block &&
                        compose
                            .onAllNodes(
                                hasContentDescription(
                                    "Paquete ${t.packets[it].ip} puerto ${t.packets[it].port}",
                                    substring = true,
                                )
                            )
                            .fetchSemanticsNodes()
                            .isNotEmpty()
                }
                ?.let { i ->
                    compose
                        .onAllNodes(
                            hasContentDescription(
                                "Paquete ${t.packets[i].ip} puerto ${t.packets[i].port}",
                                substring = true,
                            )
                        )
                        .onFirst()
                        .performSemanticsAction(
                            androidx.compose.ui.semantics.SemanticsActions.OnClick
                        )
                }
            compose.mainClock.advanceTimeBy(500)
            compose.onAllNodesWithText("Siguiente ronda").assertCountEquals(0)
            compose.onAllNodesWithText("Solución paso a paso").assertCountEquals(0)
            if (r.index == 3 && steps % 20 == 0) capture("lab-ddos-oleada4.png")
        }
        assertEquals(listOf(0, 1, 2, 3, 4), waves.toList())
        compose.mainClock.autoAdvance = true
        // Reading semantics inside the condition lets Robolectric run the main looper, where the
        // Room writes resume.
        compose.waitUntil(15000) {
            compose.waitForIdle()
            vm.result != null && vm.run?.phase == Phase.RESULT
        }
        val row = vm.result!!
        assertEquals(Ddos.WAVES, row.total)
        assertEquals(Ddos.WAVES, org.json.JSONArray(row.trials).length())
        val run = Ddos.run(boardLogs(row))!!
        assertEquals(100, run.total)
        assertTrue(run.correct in 1..100)
        assertTrue(run.score >= run.correct)
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("de 100 paquetes").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("${run.score}").assertExists()
        compose.onNodeWithText("puntos").assertExists()
        capture("lab-ddos-resultado.png")
        // The level moves with the packets: one block recorded, measured on 100.
        compose.waitUntil(5000) {
            compose.waitForIdle()
            vm.progress.value.any { it.game == Ddos.ID }
        }
        val p = vm.progress.value.first { it.game == Ddos.ID }
        assertEquals(1, p.blocks)
        assertEquals(if (run.accuracy >= 85) 1 else 0, p.up)
        // Robolectric keeps this database open for the next test: leave it as it was found.
        compose.runOnUiThread {
            vm.closeResult()
            vm.reset()
            vm.pref("motion", "true")
        }
        compose.waitUntil(10000) {
            compose.waitForIdle()
            vm.results.value.isEmpty() &&
                vm.preferences.value.any { it.key == "motion" && it.value == "true" }
        }
    }

    @Test
    fun bothArcadeGamesArePlayableAndPause() {
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("Cognitive Lab").fetchSemanticsNodes().isNotEmpty()
        }
        classic()
        compose.onNodeWithText("Arcade").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Pulso").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Luz activa").performClick()
        compose.onNodeWithText("1 luz tocada").assertExists()
        compose.onNodeWithContentDescription("Pausar").performClick()
        compose.onNodeWithText("Pausa", substring = true).assertExists()
        compose.onNodeWithContentDescription("Salir").performClick()
        // Fase 2 (docs/DISENO-JUEGOS-V2.md): Ruta espacial left the Arcade menu; Fase 3: Parejas
        // and Pulso stay as classics.
        compose.onNodeWithText("Pulso").assertExists()
        compose.onNodeWithText("Parejas matemáticas").assertExists()
        compose.onNodeWithText("Turno anti-DDoS").assertExists()
        compose.onNodeWithText("Parejas de incidentes").assertExists()
        compose.onAllNodesWithText("Ruta espacial").assertCountEquals(0)
        compose.onAllNodesWithText("XP", substring = true).assertCountEquals(0)
    }

    @Test
    fun infiniteLegacyGameContinuesPastTwelveWithoutSavingRewards() {
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("Cognitive Lab").fetchSemanticsNodes().isNotEmpty()
        }
        classic(false)
        // Fase 3: Mente shows La maleta, Vigía and Ruta de paquetes; Órbitas and Cálculo are only
        // back with "Mostrar ejercicios clásicos".
        compose.onNodeWithText("Mente").performScrollTo().performClick()
        for (title in
            listOf("La maleta del técnico", "Vigía de alertas", "Ruta de paquetes")) compose
            .onNodeWithTag("lite-catalog")
            .performScrollToNode(hasText(title))
        compose.onAllNodesWithText("Órbitas").assertCountEquals(0)
        compose.onAllNodesWithText("Turno anti-DDoS").assertCountEquals(0)
        compose.onAllNodesWithText("Cálculo").assertCountEquals(0)
        compose.onAllNodesWithText("Destellos").assertCountEquals(0)
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithContentDescription("Ajustes").performClick()
        compose
            .onNodeWithContentDescription("Mostrar ejercicios clásicos")
            .performScrollTo()
            .performClick()
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[BrainViewModel::class.java]
        compose.waitUntil(5000) {
            compose
                .onAllNodes(hasContentDescription("Mostrar ejercicios clásicos").and(isOn()))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithText("Mente").performScrollTo().performClick()
        compose.onNodeWithText("Infinito · sin tiempo").performClick()
        compose.waitUntil(5000) {
            runCatching {
                    compose.onNodeWithTag("lite-catalog").performScrollToNode(hasText("Órbitas"))
                }
                .isSuccess
        }
        compose.waitUntil(5000) {
            runCatching {
                    compose.onNodeWithTag("lite-catalog").performScrollToNode(hasText("Cálculo"))
                }
                .isSuccess
        }
        compose.onNodeWithText("Cálculo").performClick()
        compose.onNodeWithText("Empezar").performScrollTo().performClick()
        val numeric =
            SemanticsMatcher("respuesta numérica") { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any {
                    Regex("^-?\\d+$").matches(it.text)
                } == true
            }
        repeat(12) {
            compose.onAllNodes(numeric).onFirst().performScrollTo().performClick()
            compose.onNodeWithText("Siguiente ronda").performScrollTo().performClick()
        }
        compose.onNodeWithText("RONDA 13").assertExists()
        compose.onNodeWithText("SIN CRONÓMETRO · PIENSA Y COMPRUEBA").assertExists()
        assertTrue(vm.results.value.isEmpty())
    }
}
