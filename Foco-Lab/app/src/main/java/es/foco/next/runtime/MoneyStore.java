package es.foco.next.runtime;

import android.content.Context;
import android.content.SharedPreferences;
import es.foco.next.core.care.Money;
import java.time.LocalDate;
import java.util.List;

/** Presupuesto semanal, lo que se debe y lo anotado antes de entrar al banco. Solo en el teléfono. */
public final class MoneyStore {
    private MoneyStore() {}

    private static final String PREFS = "foco-dinero";

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static long budget(Context c) {
        return prefs(c).getLong("semana", 0);
    }

    public static void setBudget(Context c, long cents) {
        prefs(c).edit().putLong("semana", Math.max(0, cents)).apply();
    }

    public static long debt(Context c) {
        return prefs(c).getLong("deuda", 0);
    }

    public static void setDebt(Context c, long cents) {
        prefs(c).edit().putLong("deuda", Math.max(0, cents)).apply();
    }

    public static List<Money.Entry> entries(Context c) {
        return Money.decode(prefs(c).getString("gastos", ""));
    }

    public static void add(Context c, long cents, String note) {
        if (cents <= 0) return;
        List<Money.Entry> e = entries(c);
        e.add(new Money.Entry(RoutineStore.today(), cents, note == null ? "" : note.trim()));
        // Solo aquí: los montos no van al registro ni a lo que se exporta.
        prefs(c).edit().putString("gastos", Money.encode(Money.trim(e, RoutineStore.today()))).apply();
    }

    /** Lo que dice el búho; null si no hay nada configurado ni anotado. */
    public static String owlLine(Context c) {
        long b = budget(c), d = debt(c);
        List<Money.Entry> e = entries(c);
        if (b <= 0 && d <= 0 && e.isEmpty()) return null;
        return Money.owlLine(b, d, e, RoutineStore.today());
    }

    public static long spentToday(Context c) {
        return Money.spentToday(entries(c), RoutineStore.today());
    }
}
