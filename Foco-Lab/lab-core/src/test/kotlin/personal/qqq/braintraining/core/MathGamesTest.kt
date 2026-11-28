package personal.qqq.braintraining.core

import org.junit.Assert.*
import org.junit.Test

class MathGamesTest {
    private fun numbers(text: String) =
        Regex("\\d+").findAll(text).map { it.value.toInt() }.toList()

    @Test
    fun newProblemsHaveCorrectAnswersAndUniqueChoices() {
        val ids =
            listOf("percent", "fraction", "equation", "ratio", "budget", "unitprice", "planning")
        for (id in ids) for (level in 1..10) for (seed in 0L..299L) {
            val c = Generator.make(id, level, seed)
            val n = numbers(c.prompt)
            val expected =
                when (id) {
                    "percent" ->
                        if (c.prompt.startsWith("¿Cuánto")) n[0] * n[1] / 100
                        else if (c.prompt.contains("descuento")) n[0] * (100 - n[1]) / 100
                        else n[0] * (100 + n[1]) / 100
                    "fraction" ->
                        if (c.prompt.contains(" + ")) {
                            assertEquals(n[1], n[3])
                            (n[0] + n[2]) * n[4] / n[1]
                        } else n[0] * n[2] / n[1]
                    "equation" -> if (n.size == 2) n[1] - n[0] else (n[2] - n[1]) / n[0]
                    "ratio" -> n[1] * n[2] / n[0]
                    "budget" ->
                        if (c.prompt.contains("semanas")) n[0] / n[1] else n[0] - n[1] - n[2] - n[3]
                    "unitprice" -> if (n[1] * n[2] < n[3] * n[0]) 0 else 1
                    else -> if (c.prompt.contains("tareas")) n[0] * n[1] + n[2] else n[0] / n[1]
                }
            if (id == "unitprice") assertEquals(expected, c.answer)
            else assertEquals("$id level $level seed $seed", expected, c.choices[c.answer].toInt())
            assertEquals(c.choices.size, c.choices.distinct().size)
            assertTrue(c.explanation.contains("1."))
            assertTrue(c.explanation.contains("2."))
            assertEquals(c, Generator.make(id, level, seed))
        }
    }

    @Test
    fun arithmeticHonorsOrderAndDivision() {
        for (level in 1..10) for (seed in 0L..299L) {
            val c = Generator.make("math", level, seed)
            val n = numbers(c.prompt)
            val answer =
                when {
                    c.prompt.contains("(") -> (n[0] + n[1]) * n[2]
                    c.prompt.contains("×") -> n[0] * n[1]
                    c.prompt.contains("÷") -> n[0] / n[1]
                    c.prompt.contains("−") -> n[0] - n[1]
                    else -> n[0] + n[1]
                }
            assertEquals(answer, c.choices[c.answer].toInt())
        }
    }

    @Test
    fun practicalSuggestionsHandleEveryDate() {
        assertEquals(RealLifePractice.forDay(0), RealLifePractice.forDay(7))
        for (day in -14L..14L) {
            val task = RealLifePractice.forDay(day)
            assertTrue(task.first.isNotBlank())
            assertTrue(task.second.isNotBlank())
        }
        assertEquals(
            33,
            Games.all.size,
        ) // 28 + La maleta del técnico + Vigía de alertas (Fase 2) + Ruta de paquetes, Turno
        // anti-DDoS y Parejas de incidentes (Fase 3)
        assertEquals(
            11,
            Games.visible.size,
        ) // 6 Redes + Maleta, Vigía y Ruta de paquetes + Anti-DDoS y Parejas de incidentes
        assertEquals(Games.all.size, Games.all.map { it.id }.distinct().size)
    }
}
