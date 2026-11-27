package es.foco.next.ui

import android.app.Activity
import android.content.Context
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import es.foco.next.data.Repository
import es.foco.next.log.InspectorLog

/**
 * Un inicio que se cae deja el teléfono sin salida. Si Foco Home falla dos veces en 10 minutos,
 * arranca en una versión mínima en Vistas (buscador + lista) hasta que se pulse "Reintentar".
 */
object HomeCrashGuard {
    private const val PREFS = "foco-home-crash"
    private const val WINDOW_MS = 10 * 60_000L

    /** Solo cuentan los fallos del inicio (no los de Cognitive Lab, que también usa Compose). */
    private val HOME_CLASSES =
        listOf(
            "es.foco.next.ui.Home",
            "es.foco.next.ui.Owl",
            "es.foco.next.ui.AppDrawer",
            "es.foco.next.ui.SidePanels",
            "es.foco.next.ui.ProtectionBar",
            "es.foco.next.ui.Aduana",
            "es.foco.next.ui.CareCards",
        )

    fun install(a: Activity) {
        val app = a.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is Handler) return
        Thread.setDefaultUncaughtExceptionHandler(Handler(app, previous))
    }

    private class Handler(val app: Context, val previous: Thread.UncaughtExceptionHandler?) :
        Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread, e: Throwable) {
            runCatching {
                if (e.stackTrace.any { f -> HOME_CLASSES.any { f.className.startsWith(it) } }) {
                    val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    val now = System.currentTimeMillis()
                    val last = p.getLong("last", 0)
                    val n = if (now - last < WINDOW_MS) p.getInt("count", 0) + 1 else 1
                    p.edit()
                        .putLong("last", now)
                        .putInt("count", n)
                        .putString("error", e.javaClass.simpleName)
                        .commit()
                }
            }
            previous?.uncaughtException(t, e)
        }
    }

    fun minimal(c: Context): Boolean {
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.getInt("count", 0) >= 2 &&
            System.currentTimeMillis() - p.getLong("last", 0) < 24 * 60 * 60_000L
    }

    fun reset(c: Context) =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()

    fun error(c: Context): String =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("error", "") ?: ""
}

/** Inicio mínimo en Vistas: buscador y lista de apps. Sin Compose, sin paisaje. */
object MinimalHome {
    fun show(a: Activity) {
        runCatching {
            InspectorLog.event(
                a,
                InspectorLog.INCIDENTE,
                "",
                0,
                "inicio_modo_minimo:" + HomeCrashGuard.error(a),
                "home|min|" + java.time.LocalDate.now(),
            )
        }
        val apps = HomeApps.load(a, Repository.get(a).config())
        val col =
            LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 120, 48, 48)
                setBackgroundColor(0xFFDCE4DC.toInt())
            }
        col.addView(
            TextView(a).apply {
                text = "Inicio (versión mínima)"
                textSize = 20f
                setTextColor(0xFF1C2920.toInt())
            }
        )
        col.addView(
            TextView(a).apply {
                text = "Reintentar el diseño completo"
                setTextColor(0xFF355C40.toInt())
                setPadding(0, 24, 0, 24)
                setOnClickListener {
                    HomeCrashGuard.reset(a)
                    a.recreate()
                }
            }
        )
        val search = EditText(a).apply { hint = "Buscar una app" }
        col.addView(search)
        val list = ListView(a)
        var shown = apps
        val adapter =
            ArrayAdapter(
                a,
                android.R.layout.simple_list_item_1,
                shown.map { it.label }.toMutableList(),
            )
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            val app = shown[pos]
            if (app.section == es.foco.next.core.home.HomeSections.Section.BLOQUEADA)
                Toast.makeText(a, "Bloqueo total", Toast.LENGTH_SHORT).show()
            else HomeApps.open(a, app)
        }
        search.addTextChangedListener(
            object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}

                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}

                override fun afterTextChanged(s: android.text.Editable?) {
                    shown = HomeApps.search(apps, s?.toString() ?: "")
                    adapter.clear()
                    adapter.addAll(shown.map { it.label })
                }
            }
        )
        col.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        a.setContentView(col)
    }
}
