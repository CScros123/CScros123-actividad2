package es.foco.next.core.care;

import static org.junit.Assert.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.junit.Test;

/** v31 (escrito sin ejecutar, por indicación del usuario). */
public class RewardsTest {
    private static final LocalDate MON = LocalDate.of(2026, 11, 23);

    @Test
    public void oneCouponPerCompleteDayMaxThreeAndTheyExpire() {
        List<Rewards.Coupon> l = new ArrayList<>();
        for (int i = 0; i < 5; i++) l = Rewards.earnDay(l, MON.plusDays(i), MON.plusDays(i));
        assertEquals(3, l.size());
        // El mismo día no da dos.
        assertEquals(3, Rewards.earnDay(l, MON, MON).size());
        // A los 7 días caduca.
        assertEquals(2, Rewards.valid(l, MON.plusDays(8)).size());
        assertEquals(l.size(), Rewards.decode(Rewards.encode(l)).size());
    }

    @Test
    public void conditionsToRedeem() {
        Rewards.Coupon c = new Rewards.Coupon("d", MON, MON.plusDays(7), false);
        assertNull(Rewards.whyNot(c, false, false, false, 0));
        assertNotNull(Rewards.whyNot(c, true, false, false, 0));
        assertNotNull(Rewards.whyNot(c, false, true, false, 0));
        assertNotNull(Rewards.whyNot(c, false, false, true, 0));
        assertNotNull(Rewards.whyNot(c, false, false, false, 1));
        // La semana perfecta salta todo menos la noche.
        Rewards.Coupon w = new Rewards.Coupon("s", MON, MON.plusDays(6), true);
        assertNull(Rewards.whyNot(w, false, true, true, 5));
        assertNotNull(Rewards.whyNot(w, true, false, false, 0));
        assertEquals(30, Rewards.minutes(false));
        assertEquals(15, Rewards.minutes(true));
    }

    @Test
    public void perfectWeekNeedsEveryDayPhotosAndNoEvasion() {
        List<Rewards.DayCheck> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) days.add(new Rewards.DayCheck(4, 4, 2, 0, 0, 0));
        assertTrue(Rewards.perfectWeek(days));
        days.set(6, new Rewards.DayCheck(4, 4, 2, 1, 0, 0)); // domingo sin una foto
        assertFalse(Rewards.perfectWeek(days));
        days.set(6, new Rewards.DayCheck(4, 4, 2, 0, 0, 1)); // una evasión
        assertFalse(Rewards.perfectWeek(days));
        days.set(6, new Rewards.DayCheck(4, 3, 2, 0, 0, 0)); // una sin hacer
        assertFalse(Rewards.perfectWeek(days));
        List<Rewards.Coupon> l = Rewards.earnWeek(new ArrayList<>(), Rewards.lastMonday(MON), MON);
        assertEquals(2, l.size());
        assertEquals(2, Rewards.earnWeek(l, Rewards.lastMonday(MON), MON).size());
        assertEquals(LocalDate.of(2026, 11, 16), Rewards.lastMonday(MON));
    }

    @Test
    public void streakAndUnlocks() {
        assertEquals(3, Rewards.streak(Arrays.asList(true, false, true, true, true)));
        assertEquals(0, Rewards.streak(Arrays.asList(true, false)));
        assertEquals(Arrays.asList("buho_bufanda", "fondo_aurora"), Rewards.newUnlocks(7, new HashSet<>()));
        assertTrue(Rewards.newUnlocks(7, new HashSet<>(Arrays.asList("buho_bufanda", "fondo_aurora"))).isEmpty());
        assertEquals(14, Rewards.nextMilestone(new HashSet<>(Arrays.asList("buho_bufanda", "fondo_aurora")))[0]);
        assertTrue(Rewards.bonusAt(7));
        assertFalse(Rewards.bonusAt(6));
    }
}
