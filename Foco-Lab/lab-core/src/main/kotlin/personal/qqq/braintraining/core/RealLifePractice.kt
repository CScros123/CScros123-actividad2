package personal.qqq.braintraining.core

/** Practical prompts rotate by date, independently of the user's scores. */
object RealLifePractice {
    private val tasks =
        listOf(
            "Compara antes de comprar" to
                "Elige dos productos. Divide cada precio por su peso o cantidad y anota cuál conviene. Comprueba que comparas la misma unidad.",
            "Planifica con margen" to
                "Estima cuánto tardarán tres tareas de hoy. Añade una pausa y un margen. Al terminar, compara tu estimación con el tiempo real.",
            "Calcula un descuento" to
                "Toma un precio y calcula un descuento del 15 %: encuentra el 10 % y el 5 %, súmalos y réstalos del precio. Verifica con calculadora.",
            "Divide un problema" to
                "Escoge un problema pendiente. Escribe qué sabes, qué necesitas averiguar y tres pasos pequeños. Completa el primero y revisa qué cambió.",
            "Haz un presupuesto" to
                "Anota tu dinero disponible, un gasto necesario y una cantidad para ahorro. Calcula lo restante antes de decidir una compra.",
            "Escala una receta" to
                "Elige una receta, calcula la cantidad por persona y ajústala al número de personas que comerán. Revisa gramos, tazas y porciones.",
            "Comprueba tus supuestos" to
                "Ante una decisión, escribe dos opciones y qué estás suponiendo sobre costo, tiempo o resultado. Busca un dato que pueda cambiar tu elección.",
        )

    fun forDay(epochDay: Long) = tasks[Math.floorMod(epochDay, tasks.size.toLong()).toInt()]
}
