package es.foco.next.core.care;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class MoneyTest {
    private static final LocalDate WED = LocalDate.of(2026, 11, 25);

    @Test
    public void parsesAmounts() {
        assertEquals(1200, Money.parse("12"));
        assertEquals(1250, Money.parse("12.50"));
        assertEquals(1250, Money.parse("12,5"));
        assertEquals(700, Money.parse("B/. 7"));
        assertEquals(-1, Money.parse("doce"));
        assertEquals("$12.50", Money.fmt(1250));
        assertEquals("$7", Money.fmt(700));
    }

    @Test
    public void dailyAllowanceSpreadsWhatIsLeft() {
        List<Money.Entry> e = new ArrayList<>();
        e.add(new Money.Entry(WED.minusDays(2), 2000, "lunes")); // lunes
        // 70 a la semana, 20 gastados antes del miércoles → 50 entre 5 días = 10
        assertEquals(1000, Money.dailyAllowance(7000, e, WED));
        e.add(new Money.Entry(WED, 300, "café"));
        assertEquals(700, Money.leftToday(7000, e, WED));
        assertEquals(2300, Money.spentWeek(e, WED));
    }

    @Test
    public void owlNeverScolds() {
        List<Money.Entry> e = new ArrayList<>();
        e.add(new Money.Entry(WED, 1500, "súper"));
        String s = Money.owlLine(7000, 0, e, WED);
        assertTrue(s, s.contains("sin drama"));
        assertTrue(Money.owlLine(0, 0, new ArrayList<>(), WED).contains("presupuesto"));
    }

    @Test
    public void debtIdea() {
        assertNull(Money.debtIdea(0, 1000));
        assertEquals("Si apartas $1 al día, saldas $60 en 60 días.", Money.debtIdea(6000, 1000));
        assertEquals("Si apartas $2 al día, saldas $60 en 30 días.", Money.debtIdea(6000, 1500));
    }

    @Test
    public void roundTrip() {
        List<Money.Entry> e = new ArrayList<>();
        e.add(new Money.Entry(WED, 1250, "almuerzo|x"));
        List<Money.Entry> back = Money.decode(Money.encode(e));
        assertEquals(1250, back.get(0).cents);
        assertEquals("almuerzo/x", back.get(0).note);
        assertEquals(0, Money.trim(back, WED.plusWeeks(9)).size());
    }
}
