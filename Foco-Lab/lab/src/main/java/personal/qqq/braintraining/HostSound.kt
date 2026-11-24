package personal.qqq.braintraining

import android.content.Context
import kotlin.math.pow

/**
 * Fase 6: puente de sonido con Foco (misma app y mismo proceso, sin dependencia de :app). CONTRATO:
 * lee las preferencias "foco-soundscape" que escribe es.foco.next.runtime.SoundSettings: `silent`
 * (booleano, por defecto false) y `effects_volume` (0..100, por defecto 100). Si Foco no las
 * escribió nunca, Lab suena como en Arcade 2.1.
 */
internal object HostSound {
    const val PREFS = "foco-soundscape"
    const val SILENT = "silent"
    const val EFFECTS = "effects_volume"

    fun silent(context: Context): Boolean =
        runCatching {
                context.applicationContext.getSharedPreferences(PREFS, 0).getBoolean(SILENT, false)
            }
            .getOrDefault(false)

    /** Factor de 0 a 1 para los efectos de juego. */
    fun effects(context: Context): Float =
        runCatching {
                context.applicationContext.getSharedPreferences(PREFS, 0).getInt(EFFECTS, 100)
            }
            .getOrDefault(100)
            .coerceIn(0, 100) / 100f
}

/**
 * Fase 6: sonidos de juego procedurales a partir de las muestras originales de Lab (sin archivos
 * nuevos). El acierto sube por una escala pentatónica mayor según la racha de la sesión (cambio de
 * velocidad en SoundPool, que acepta 0,5..2,0); el error es más suave y algo más grave; el cierre
 * de sesión usa lab_complete.
 */
internal object GameTones {
    /**
     * Semitonos de la pentatónica mayor alrededor de la nota original: sol, la, do, re, mi, sol.
     */
    private val STEPS = intArrayOf(-5, -3, 0, 2, 4, 7)

    /** Velocidad de reproducción para el acierto número [streak] seguido (1 = primer acierto). */
    fun correctRate(streak: Int): Float =
        semitones(STEPS[(streak.coerceAtLeast(1) - 1).coerceAtMost(STEPS.size - 1)])

    /** Error: un tono entero por debajo, sin brusquedad. */
    const val RETRY_RATE = 0.89f
    /**
     * Órbitas 2: one note per orbit position, pentatónica mayor (do, re, mi, sol, la) en dos
     * octavas, dentro de 0,5–2,0.
     */
    private val NOTES = intArrayOf(-9, -7, -5, -3, 0, 2, 4, 7, 9, 12)

    fun note(position: Int): Float =
        semitones(NOTES[((position % NOTES.size) + NOTES.size) % NOTES.size])

    const val NOTE_VOLUME = .30f

    fun semitones(n: Int): Float = 2.0.pow(n / 12.0).toFloat().coerceIn(0.5f, 2.0f)

    /**
     * Mezcla baja: base de cada efecto antes del volumen de Foco. Toque, acierto, error y cierre.
     */
    fun baseVolume(cue: FeedbackCue): Float =
        when (cue) {
            FeedbackCue.TAP -> .20f
            FeedbackCue.CORRECT -> .36f
            FeedbackCue.RETRY -> .26f
            FeedbackCue.COMPLETE -> .40f
            FeedbackCue.LEVEL_UP -> .40f
            FeedbackCue.UNLOCK -> .36f
            FeedbackCue.COMBO -> .32f
            FeedbackCue.ALARM -> .16f
            FeedbackCue.WHOOSH -> .14f
            FeedbackCue.PULSE -> .07f
            FeedbackCue.PULSE_ACCENT -> .12f
        }

    /** Subida: el cierre una cuarta más agudo. Desbloqueo: el acierto una quinta más agudo. */
    val LEVEL_UP_RATE = semitones(5)
    val UNLOCK_RATE = semitones(7)

    /**
     * Muestra que suena para cada señal: las dos nuevas reutilizan las existentes con otro tono.
     */
    fun sample(cue: FeedbackCue): Int =
        when (cue) {
            FeedbackCue.TAP -> R.raw.lab_touch
            FeedbackCue.CORRECT,
            FeedbackCue.UNLOCK -> R.raw.lab_correct
            FeedbackCue.RETRY -> R.raw.lab_retry
            FeedbackCue.COMPLETE,
            FeedbackCue.LEVEL_UP -> R.raw.lab_complete
            FeedbackCue.COMBO -> R.raw.lab_correct
            FeedbackCue.ALARM -> R.raw.lab_touch
            FeedbackCue.WHOOSH -> R.raw.lab_touch
            FeedbackCue.PULSE,
            FeedbackCue.PULSE_ACCENT -> R.raw.lab_touch
        }

    /**
     * COMBO step [n] (1 = first link): CORRECT raised n − 1 semitones, at most +7 (§1.5, `rate =
     * 2^(n/12)`).
     */
    fun comboRate(n: Int): Float = semitones((n.coerceAtLeast(1) - 1).coerceAtMost(7))

    /** ALARM: two alternating beeps a fourth apart. WHOOSH: the touch an octave down, soft. */
    val ALARM_HIGH = semitones(9)
    val ALARM_LOW = semitones(4)
    val WHOOSH_RATE = semitones(-12)
    /**
     * Pulse of Turno anti-DDoS: the touch a fifth down on the weak beat; the accent (a packet
     * enters) at its own pitch and louder.
     */
    val PULSE_RATE = semitones(-7)
}

/**
 * Fase 8: puente de movimiento con Foco. CONTRATO: preferencias "foco-ambient" que escribe Foco
 * (ver es.foco.next.ui.PauseVideoView#animationAllowed): `reduce` (reducir movimiento) y `saving`
 * (ahorro de energía). Si cualquiera está activa, Lab quita las animaciones decorativas. Se lee al
 * recomponer ExperienceHost.
 */
internal object HostMotion {
    const val PREFS = "foco-ambient"

    fun reduced(context: Context): Boolean =
        runCatching {
                val p = context.applicationContext.getSharedPreferences(PREFS, 0)
                p.getBoolean("reduce", false) || p.getBoolean("saving", false)
            }
            .getOrDefault(false)
}
