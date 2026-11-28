package es.foco.next.core.backup;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * v50: la semana en palabras. El resumen del búho del domingo (con los números de la hoja) y lo que
 * bloqueó NextDNS en la semana para Análisis. Lo bueno primero, sin culpa y sin rojo.
 */
public final class WeekLines {
    private WeekLines() {}

    /** Nombre bonito de cada red (las claves de {@link NextDnsPlan#socialActivity}). */
    static String label(String network) {
        switch (network) {
            case "tiktok":
                return "TikTok";
            case "instagram":
                return "Instagram";
            case "facebook":
                return "Facebook";
            case "youtube":
                return "YouTube";
            case "reddit":
                return "Reddit";
            case "pinterest":
                return "Pinterest";
            case "snapchat":
                return "Snapchat";
            case "twitter":
                return "X";
            default:
                return network;
        }
    }

    /**
     * Líneas de «Tu semana con NextDNS» para Análisis.
     *
     * @param social intentos bloqueados por red (7 días)
     * @param silenced avisos de redes que Foco quitó de noche (7 días)
     */
    public static List<String> dnsWeek(
            long blocked, long total, Map<String, Long> social, int silenced, boolean numbers) {
        List<String> out = new ArrayList<>();
        if (total > 0) {
            double pct = Math.round(blocked * 1000.0 / total) / 10.0;
            out.add(
                    numbers
                            ? "NextDNS frenó "
                                    + blocked
                                    + " conexiones de "
                                    + total
                                    + " ("
                                    + String.format(Locale.ROOT, "%.1f", pct)
                                    + " %)."
                            : "NextDNS te cuidó toda la semana.");
        }
        long sum = 0;
        String top = null;
        long topN = 0;
        if (social != null)
            for (Map.Entry<String, Long> e : social.entrySet()) {
                sum += e.getValue();
                if (e.getValue() > topN) {
                    topN = e.getValue();
                    top = e.getKey();
                }
            }
        if (sum > 0)
            out.add(
                    numbers
                            ? "Las redes intentaron conectarse "
                                    + sum
                                    + " veces y no pudieron; sobre todo "
                                    + label(top)
                                    + "."
                            : "Las redes intentaron conectarse y no pudieron; sobre todo "
                                    + label(top)
                                    + ".");
        if (silenced > 0)
            out.add(
                    numbers
                            ? "De noche quité "
                                    + silenced
                                    + (silenced == 1 ? " aviso" : " avisos")
                                    + " de redes para que descansaras sin interrupciones."
                            : "De noche quité los avisos de redes para que descansaras sin interrupciones.");
        return out;
    }

    /**
     * El mensaje del búho del domingo con los números de la hoja: invertido y ocio de esta semana
     * frente a la anterior y el peor horario. Lo invertido primero; si el ocio subió, sin regañar.
     */
    public static String owlWeekly(
            long investedThis,
            long investedPrev,
            long leisureThis,
            long leisurePrev,
            String worstHour) {
        StringBuilder b = new StringBuilder("Tu semana, bro: ");
        b.append(investedThis).append(" min invertidos");
        if (investedPrev > 0 && investedThis > investedPrev)
            b.append(" (").append(investedThis - investedPrev).append(" más que la anterior 💪)");
        b.append(". Ocio: ").append(leisureThis).append(" min");
        if (leisurePrev > 0 && leisureThis < leisurePrev)
            b.append(", ").append(leisurePrev - leisureThis).append(" menos que la otra semana");
        b.append('.');
        if (worstHour != null && worstHour.matches("\\d{2}:00"))
            b.append(" Tu hora más difícil fue la de las ")
                    .append(worstHour)
                    .append("; mañana la cuidamos juntos.");
        return b.toString();
    }

    /**
     * ¿Toca el resumen? Domingo (o lunes si el domingo no hubo copia), una vez por semana.
     * {@code lastWeek}: domingo del último resumen enviado (AAAA-MM-DD) o "".
     */
    public static boolean weeklyDue(LocalDate today, String lastWeek) {
        LocalDate sunday =
                today.getDayOfWeek() == DayOfWeek.SUNDAY
                        ? today
                        : today.getDayOfWeek() == DayOfWeek.MONDAY ? today.minusDays(1) : null;
        return sunday != null && !sunday.toString().equals(lastWeek);
    }

    /** Domingo de la semana del resumen (para guardar que ya se envió). */
    public static LocalDate weekSunday(LocalDate today) {
        return today.getDayOfWeek() == DayOfWeek.MONDAY ? today.minusDays(1) : today;
    }
}
