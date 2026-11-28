package es.foco.next.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Frases de apoyo para la pantalla de pausa. Java puro y determinista: con la misma semilla (por
 * ejemplo, el día) y el mismo índice siempre devuelve la misma frase, recorre todo el conjunto sin
 * repetir y nunca muestra la misma frase dos veces seguidas.
 */
public final class PauseMessages {
    private PauseMessages() {}

    public enum Category {
        ANIMO("Ánimo"),
        APRENDER("Aprende algo"),
        CUERPO("Tu cuerpo"),
        FE("Fe");
        public final String label;

        Category(String label) {
            this.label = label;
        }
    }

    /** Una frase con su categoría (para el rótulo de la tarjeta). */
    public static final class Message {
        public final Category category;
        public final String text;

        Message(Category category, String text) {
            this.category = category;
            this.text = text;
        }
    }

    private static final String[] ANIMO = {
        "Vamos a lograr algo hoy. Aunque sea pequeño.",
        "Hey, aquí sigues. Eso ya cuenta.",
        "Un paso pequeño también te mueve hacia adelante.",
        "No tienes que hacerlo perfecto, solo empezar.",
        "Lo que haces en los próximos cinco minutos importa.",
        "Ya elegiste parar. Ahora elige algo que te haga bien.",
        "Tu atención vale mucho. Dásela a algo que quieras.",
        "Hoy puedes avanzar un poquito en lo que te importa.",
        "Respira. Vuelve. Sigue. Así se construye el foco.",
        "Cada vez que regresas a tu plan, te haces más fuerte.",
        "Ese pendiente se ve más pequeño cuando lo empiezas.",
        "Estás entrenando algo valioso: decidir a dónde va tu tiempo.",
        "Vas mejor de lo que crees. Sigue así.",
        "Termina una cosa sencilla y date crédito por ella.",
        "Más tarde vas a agradecer lo que hagas ahora.",
        "No te falta capacidad, solo un primer movimiento.",
    };
    private static final String[] APRENDER = {
        "¿Sabías que en 5 minutos puedes leer unas 2 páginas?",
        "¿Y si aprendes algo nuevo antes de volver?",
        "Abre ese libro que dejaste a medias. Solo una página.",
        "Practica 3 minutos en Lab: poco a poco se nota.",
        "Diez palabras nuevas al día son más de 3,000 al año.",
        "Escribe en una línea lo que aprendiste hoy.",
        "Busca el significado de algo que siempre te dio curiosidad.",
        "Repasa tus apuntes cinco minutos; tu memoria te lo agradecerá.",
        "Un problema de práctica ahora vale más que diez videos después.",
        "Lee un artículo corto sobre algo que quieras dominar.",
        "Escucha un audio que te enseñe algo en lugar de deslizar.",
        "Explica en voz alta un tema que estés estudiando.",
        "¿Ya probaste una lección corta de idiomas hoy?",
        "Anota una pregunta y busca la respuesta con calma.",
        "Dibuja, toca un instrumento o escribe: crear también es aprender.",
        "Aprender un poco cada día le gana a aprender mucho un solo día.",
    };
    private static final String[] CUERPO = {
        "Respira hondo 3 veces. Despacio.",
        "Toma un vaso de agua.",
        "Estírate: brazos arriba, hombros abajo.",
        "Relaja la mandíbula y suelta los hombros.",
        "Mira algo lejano durante 20 segundos para descansar la vista.",
        "Levántate y camina un minuto.",
        "Endereza la espalda y apoya bien los pies.",
        "Inhala en 4 tiempos, sostén 4, exhala en 6.",
        "Abre la ventana o busca un poco de luz natural.",
        "Gira el cuello suavemente hacia un lado y luego al otro.",
        "Parpadea despacio varias veces; tus ojos lo necesitan.",
        "Pon una mano en el pecho y siente tu respiración.",
        "Haz diez sentadillas o diez círculos con los brazos.",
        "Come algo ligero si tienes hambre; tu mente también lo nota.",
        "Suelta el teléfono un momento y estira los dedos.",
    };
    private static final String[] FE = {
        "Venid a mí todos los que estáis trabajados y cargados, que yo os haré descansar. — Mateo 11:28",
        "Estad quietos, y conoced que yo soy Dios. — Salmos 46:10",
        "Todo lo puedo en Cristo que me fortalece. — Filipenses 4:13",
        "Jehová es mi pastor; nada me faltará. — Salmos 23:1",
        "La paz os dejo, mi paz os doy. No se turbe vuestro corazón, ni tenga miedo. — Juan 14:27",
        "Encomienda a Jehová tus obras, y tus pensamientos serán afirmados. — Proverbios 16:3",
        "Este es el día que hizo Jehová: nos gozaremos y alegraremos en él. — Salmos 118:24",
        "Echando toda vuestra solicitud en él, porque él tiene cuidado de vosotros. — 1 Pedro 5:7",
        "Enséñanos de tal modo a contar nuestros días, que traigamos al corazón sabiduría. — Salmos 90:12",
        "Para todas las cosas hay sazón, y todo lo que se quiere debajo del cielo tiene su tiempo. — Eclesiastés 3:1",
        "Los que esperan a Jehová tendrán nuevas fuerzas. — Isaías 40:31",
        "En el silencio también se ora. Quédate un momento aquí.",
        "Tu tiempo es un regalo. Úsalo con amor.",
        "Haz una pausa y da gracias por algo de hoy.",
        "No cargas solo con todo. Suelta un poco y respira.",
        "Ofrece este rato a algo bueno: tu trabajo, tu estudio, alguien que quieres.",
    };

    public static List<String> of(Category category) {
        String[] source =
                category == Category.ANIMO
                        ? ANIMO
                        : category == Category.APRENDER
                                ? APRENDER
                                : category == Category.CUERPO ? CUERPO : FE;
        return Collections.unmodifiableList(java.util.Arrays.asList(source));
    }

    /** Todas las frases de las categorías elegidas, en orden fijo. Sin categorías, usa Ánimo. */
    public static List<Message> pool(Set<Category> enabled) {
        Set<Category> cats =
                enabled == null || enabled.isEmpty()
                        ? EnumSet.of(Category.ANIMO)
                        : EnumSet.copyOf(enabled);
        List<Message> out = new ArrayList<>();
        for (Category c : Category.values())
            if (cats.contains(c)) for (String s : of(c)) out.add(new Message(c, s));
        return out;
    }

    /**
     * Frase número {@code index} del recorrido para {@code seed}. Cada vuelta usa una mezcla
     * distinta y se asegura de que la primera de una vuelta no repita la última de la anterior.
     */
    public static Message next(Set<Category> enabled, long seed, long index) {
        List<Message> pool = pool(enabled);
        int n = pool.size();
        long i = Math.max(0, index);
        long round = i / n;
        int pos = (int) (i % n);
        int[] order = order(n, seed, round);
        if (round > 0 && n > 1) {
            int previousLast = order(n, seed, round - 1)[n - 1];
            if (order[0] == previousLast) {
                int t = order[0];
                order[0] = order[1];
                order[1] = t;
            }
        }
        return pool.get(order[pos]);
    }

    private static int[] order(int n, long seed, long round) {
        int[] o = new int[n];
        for (int k = 0; k < n; k++) o[k] = k;
        Random r = new Random(seed * 0x9E3779B97F4A7C15L + round * 1_000_003L + n);
        for (int k = n - 1; k > 0; k--) {
            int j = r.nextInt(k + 1);
            int t = o[k];
            o[k] = o[j];
            o[j] = t;
        }
        return o;
    }
}
