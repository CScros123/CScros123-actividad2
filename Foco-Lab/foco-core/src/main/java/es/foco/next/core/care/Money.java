package es.foco.next.core.care;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Gasto diario sin mortificar: presupuesto semanal, lo anotado antes de entrar al banco, cuánto
 * queda hoy y una idea para lo que se debe. Céntimos en long (sin decimales flotantes).
 */
public final class Money {
    private Money() {}

    public static final class Entry {
        public final LocalDate day;
        public final long cents;
        public final String note;

        public Entry(LocalDate day, long cents, String note) {
            this.day = day;
            this.cents = cents;
            this.note = note == null ? "" : note;
        }
    }

    /** "12", "12.50", "12,5", "$ 12", "B/. 7" → céntimos; -1 si no es un monto válido. */
    public static long parse(String raw) {
        if (raw == null) return -1;
        String t = raw.trim().replace("B/.", "").replace("$", "").replace(" ", "").replace(",", ".");
        if (!t.matches("\\d{1,7}(\\.\\d{1,2})?")) return -1;
        int dot = t.indexOf('.');
        if (dot < 0) return Long.parseLong(t) * 100;
        String dec = (t.substring(dot + 1) + "0").substring(0, 2);
        return Long.parseLong(t.substring(0, dot)) * 100 + Long.parseLong(dec);
    }

    public static String fmt(long cents) {
        boolean neg = cents < 0;
        long a = Math.abs(cents);
        String s = a % 100 == 0 ? "$" + a / 100 : String.format(Locale.ROOT, "$%d.%02d", a / 100, a % 100);
        return neg ? "-" + s : s;
    }

    public static LocalDate weekStart(LocalDate d) {
        return d.with(DayOfWeek.MONDAY);
    }

    public static long spent(List<Entry> entries, LocalDate from, LocalDate toInclusive) {
        long n = 0;
        for (Entry e : entries) if (!e.day.isBefore(from) && !e.day.isAfter(toInclusive)) n += e.cents;
        return n;
    }

    public static long spentToday(List<Entry> entries, LocalDate today) {
        return spent(entries, today, today);
    }

    public static long spentWeek(List<Entry> entries, LocalDate today) {
        return spent(entries, weekStart(today), today);
    }

    /** Lo que toca hoy: lo que queda de la semana (sin contar hoy) repartido entre hoy y los días que faltan. */
    public static long dailyAllowance(long weekBudget, List<Entry> entries, LocalDate today) {
        if (weekBudget <= 0) return 0;
        long before = spent(entries, weekStart(today), today.minusDays(1));
        int daysLeft = 8 - today.getDayOfWeek().getValue(); // lunes 7 … domingo 1
        return Math.max(0, (weekBudget - before) / daysLeft);
    }

    public static long leftToday(long weekBudget, List<Entry> entries, LocalDate today) {
        return dailyAllowance(weekBudget, entries, today) - spentToday(entries, today);
    }

    /** Idea para lo que se debe: apartar un poco cada día. null si no hay deuda. */
    public static String debtIdea(long debt, long daily) {
        if (debt <= 0) return null;
        long step = Math.max(100, ((daily / 10) + 99) / 100 * 100); // ~10 % de lo diario, mínimo $1, redondeado a dólares
        long days = (debt + step - 1) / step;
        return "Si apartas " + fmt(step) + " al día, saldas " + fmt(debt) + " en " + days + (days == 1 ? " día." : " días.");
    }

    /** Lo que dice el búho (al despertar y en el inicio). Nunca regaña. */
    public static String owlLine(long weekBudget, long debt, List<Entry> entries, LocalDate today) {
        StringBuilder b = new StringBuilder();
        if (weekBudget <= 0) {
            long t = spentToday(entries, today);
            b.append(t > 0 ? "Hoy llevas " + fmt(t) + "." : "Pon un presupuesto semanal y te digo cuánto tienes cada día.");
        } else {
            long left = leftToday(weekBudget, entries, today);
            if (left >= 0) b.append("Para hoy te quedan ").append(fmt(left)).append(left > 0 ? ". Vas bien." : ". Justo.");
            else b.append("Hoy vas ").append(fmt(-left)).append(" por encima; se reparte solo en los días que quedan, sin drama.");
        }
        String idea = debtIdea(debt, dailyAllowance(weekBudget, entries, today));
        if (idea != null) b.append(' ').append(idea);
        return b.toString();
    }

    // ---------------- Guardado: una línea por gasto "2026-11-22|1250|almuerzo" ----------------

    public static String encode(List<Entry> entries) {
        StringBuilder b = new StringBuilder();
        for (Entry e : entries) {
            if (b.length() > 0) b.append('\n');
            b.append(e.day).append('|').append(e.cents).append('|').append(e.note.replace("\n", " ").replace("|", "/"));
        }
        return b.toString();
    }

    public static List<Entry> decode(String s) {
        List<Entry> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        for (String line : s.split("\n")) {
            String[] p = line.split("\\|", 3);
            if (p.length < 2) continue;
            try {
                out.add(new Entry(LocalDate.parse(p[0]), Long.parseLong(p[1]), p.length > 2 ? p[2] : ""));
            } catch (RuntimeException ignored) {
            }
        }
        return out;
    }

    /** Solo se guardan las últimas 8 semanas. */
    public static List<Entry> trim(List<Entry> entries, LocalDate today) {
        LocalDate from = weekStart(today).minusWeeks(7);
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) if (!e.day.isBefore(from)) out.add(e);
        return out;
    }
}
