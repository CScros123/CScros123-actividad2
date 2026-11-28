package personal.qqq.braintraining

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import personal.qqq.braintraining.core.*
import personal.qqq.braintraining.design.BrainTheme

/**
 * Lote B: Relámpago and the 3-minute practice play the pilot boards, and with "Animaciones" off
 * they settle in a single frame.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuickBoardsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var answered: List<Int>? = null

    private fun show(c: Challenge) {
        answered = null
        compose.setContent {
            BrainTheme {
                CompositionLocalProvider(LocalMotionEnabled provides false) {
                    var picked by remember { mutableStateOf<List<Int>?>(null) }
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        QuickChallenge(c, picked == null, picked != null, picked ?: emptyList()) {
                            picked = it
                            answered = it
                        }
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeByFrame()
    }

    @Test
    fun bitSwitchesSumLiveAndSubmitTheValue() {
        val c =
            (0L..50L).map { Quick.make("bits", 3, it) }.first { it.bits!!.format == Pilot.BUILD }
        val t = c.bits!!
        show(c)
        t.weights.forEachIndexed { i, w ->
            if (t.bit(t.target, i) == 1) {
                compose.onNodeWithContentDescription("Bit de valor $w").performClick()
                compose.mainClock.advanceTimeByFrame()
            }
        }
        compose.onNodeWithText("${t.target}").assertExists()
        compose.onNodeWithText("Comprobar").performClick()
        compose.mainClock.advanceTimeByFrame()
        assertEquals(listOf(t.target), answered)
        assertTrue(c.accepts(answered!!))
    }

    @Test
    fun inboxAnswersWithTheButtonsAndShowsTheClueAtOnce() {
        val c = Quick.make("phishing", 2, 9)
        val t = c.inbox!!
        show(c)
        compose.onNodeWithText(if (t.phishing) "Trampa" else "Seguro").performClick()
        compose.mainClock.advanceTimeByFrame()
        assertTrue(c.accepts(answered!!))
        compose
            .onNodeWithText(
                (if (t.phishing) "Era una trampa · pista: " else "Era seguro · clave: ") + t.clue
            )
            .assertIsDisplayed()
    }

    @Test
    fun firewallAppliesTheRulesFromTheBoard() {
        val c = Quick.make("ports", 2, 4)
        val t = c.firewall!!
        show(c)
        t.rows.forEachIndexed { i, row ->
            if (t.start[i] != t.expected[i]) {
                compose.onNodeWithText(":${row.port}").performClick()
                compose.mainClock.advanceTimeByFrame()
            }
        }
        compose.onNodeWithText("Aplicar reglas").performScrollTo().performClick()
        compose.mainClock.advanceTimeByFrame()
        assertTrue(c.accepts(answered!!))
    }

    /**
     * Paso 1: from level 3 a row shows only its port; after applying, the service, the verdict and
     * the one-line lesson appear at once.
     */
    @Test
    fun firewallRowsHideTheServiceAndTeachAfterApplying() {
        val c =
            (0L..50L)
                .map { Quick.make("ports", 5, it) }
                .first { it.firewall!!.format == Pilot.BLOCK }
        val t = c.firewall!!
        show(c)
        t.rows.forEach {
            compose.onNodeWithText(":${it.port}").assertExists()
            compose.onNodeWithText(it.service).assertDoesNotExist()
        }
        compose.onNodeWithText("Método").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText(t.hint).assertExists()
        compose.onNodeWithText("Aplicar reglas").performScrollTo().performClick()
        compose.mainClock.advanceTimeByFrame()
        assertFalse(c.accepts(answered!!)) // the panel was left as it started
        compose.onNodeWithText(t.why).assertExists()
        compose.onNodeWithText(t.rows[t.expected.indexOf(false)].service).assertExists()
        compose.onAllNodesWithText("Debía quedar cerrado").onFirst().assertExists()
    }

    @Test
    fun ruleOrderBoardListsTheRulesAndAcceptsTheFirstMatchAnswer() {
        val c =
            (0L..80L)
                .map { Quick.make("ports", 9, it) }
                .first { it.firewall!!.format == Pilot.ORDER }
        val t = c.firewall!!
        show(c)
        t.rules.forEachIndexed { i, r ->
            compose.onNodeWithText("${i+1}  ${r.label}").assertExists()
        }
        compose.onNodeWithText("—  sin coincidencia: DENEGAR").assertExists()
        t.rows.forEachIndexed { i, row ->
            if (t.start[i] != t.expected[i]) {
                compose.onNodeWithText(":${row.port}").performScrollTo().performClick()
                compose.mainClock.advanceTimeByFrame()
            }
        }
        compose.onNodeWithText("Aplicar reglas").performScrollTo().performClick()
        compose.mainClock.advanceTimeByFrame()
        assertTrue(c.accepts(answered!!))
        compose
            .onNodeWithText("Reglas correctas · ${t.rows.size} de ${t.rows.size} puertos bien")
            .assertExists()
        t.rows
            .groupBy { FirstMatch.decided(t.rules, it.port) }
            .forEach { (text, rows) ->
                assertEquals(rows.size, compose.onAllNodesWithText(text).fetchSemanticsNodes().size)
            }
    }

    @Test
    fun subnetBoardSubmitsThePrefix() {
        val c = Quick.make("subnet", 2, 3)
        val t = c.subnet!!
        show(c)
        repeat(kotlin.math.abs(t.answer - t.start)) {
            compose
                .onNodeWithContentDescription(
                    if (t.answer > t.start) "Un bit más de red" else "Un bit menos de red"
                )
                .performClick()
            compose.mainClock.advanceTimeByFrame()
        }
        compose.onNodeWithText("Comprobar /${t.answer}").performScrollTo().performClick()
        compose.mainClock.advanceTimeByFrame()
        assertEquals(listOf(t.answer), answered)
        assertTrue(c.accepts(answered!!))
    }
}
