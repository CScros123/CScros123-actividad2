package personal.qqq.braintraining

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import personal.qqq.braintraining.core.*
import personal.qqq.braintraining.design.BrainTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualScenesTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String) {
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val f = File("build/screenshots/$name")
            f.parentFile?.mkdirs()
            f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test
    fun ringNodesRemainIndividuallyAccessibleAndTapInPlace() {
        var page by mutableIntStateOf(0)
        val orbit = Generator.make("orbit", 3, 44)
        val compass = Generator.make("compass", 7, 13)
        compose.setContent {
            BrainTheme {
                Column(
                    Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Text(
                        if (page == 0) "Órbitas" else "Rumbo inverso",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    if (page == 0) {
                        OrbitBoard(orbit.orbit!!, false) { _, _, _ -> }
                    } else {
                        CompassBoard(compass)
                        Text(compass.prompt, style = MaterialTheme.typography.headlineMedium)
                        BoardGrid(2, 4) { i ->
                            OutlinedButton({}, Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
                                Text(compass.choices[i])
                            }
                        }
                    }
                }
            }
        }
        // Órbitas 2: no numbers on screen; screen readers still reach every node, named by its
        // clock position.
        for (h in listOf(12, 2, 3, 5, 6, 8, 9, 11)) compose
            .onNodeWithContentDescription("Nodo a las $h")
            .assertIsDisplayed()
        compose.onAllNodesWithText("1").assertCountEquals(0)
        capture("orbitas-tablero.png")
        compose.runOnIdle { page = 1 }
        compose.onNodeWithText(compass.prompt).assertIsDisplayed()
        capture("rumbo-tablero.png")
    }
}
