package es.foco.next.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope

/**
 * v49 (batería): como LaunchedEffect, pero solo mientras la pantalla se ve (STARTED). Un bucle con
 * delay() seguía despertando el procesador con el inicio en segundo plano; así se para al salir y
 * vuelve a empezar al volver.
 */
@Composable
fun WhileVisible(vararg keys: Any?, block: suspend CoroutineScope.() -> Unit) {
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, *keys) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED, block) }
}
