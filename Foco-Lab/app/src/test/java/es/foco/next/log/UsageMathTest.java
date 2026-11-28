package es.foco.next.log;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

/** Fase 4: foreground time from Android usage events (pure JVM). */
public class UsageMathTest {
    private static UsageMath.Mark m(long t, String p, int k) {
        return new UsageMath.Mark(t, p, k);
    }

    @Test
    public void sumsResumedToPausedPerApp() {
        Map<String, Long> r =
                UsageMath.foreground(
                        Arrays.asList(
                                m(100, "a", UsageMath.START),
                                m(400, "a", UsageMath.STOP),
                                m(500, "b", UsageMath.START),
                                m(600, "b", UsageMath.STOP),
                                m(700, "a", UsageMath.START),
                                m(800, "a", UsageMath.STOP)),
                        0,
                        1000,
                        null);
        assertEquals(Long.valueOf(400), r.get("a"));
        assertEquals(Long.valueOf(100), r.get("b"));
    }

    @Test
    public void switchingClosesThePreviousAppAndLatePauseIsIgnored() {
        Map<String, Long> r =
                UsageMath.foreground(
                        Arrays.asList(
                                m(0, "a", UsageMath.START),
                                m(300, "b", UsageMath.START),
                                m(310, "a", UsageMath.STOP),
                                m(500, "b", UsageMath.STOP)),
                        0,
                        1000,
                        null);
        assertEquals(Long.valueOf(300), r.get("a"));
        assertEquals(Long.valueOf(200), r.get("b"));
    }

    @Test
    public void screenOffClosesAndOpenAppIsClippedToRange() {
        Map<String, Long> r =
                UsageMath.foreground(
                        Arrays.asList(
                                m(100, "a", UsageMath.START),
                                m(200, "", UsageMath.CLOSE_ALL),
                                m(900, "b", UsageMath.START)),
                        0,
                        1000,
                        null);
        assertEquals(Long.valueOf(100), r.get("a"));
        assertEquals(Long.valueOf(100), r.get("b"));
    }

    @Test
    public void eventsOutsideTheRangeAreClipped() {
        Map<String, Long> r =
                UsageMath.foreground(
                        Arrays.asList(m(-500, "a", UsageMath.START), m(2000, "a", UsageMath.STOP)),
                        0,
                        1000,
                        null);
        assertEquals(Long.valueOf(1000), r.get("a"));
    }

    @Test
    public void onlyRequestedPackagesAreReturned() {
        Map<String, Long> r =
                UsageMath.foreground(
                        Arrays.asList(
                                m(0, "a", UsageMath.START),
                                m(10, "b", UsageMath.START),
                                m(20, "b", UsageMath.STOP)),
                        0,
                        100,
                        Collections.singleton("b"));
        assertEquals(Collections.singleton("b"), r.keySet());
    }

    @Test
    public void emptyOrInvalidRanges() {
        assertTrue(UsageMath.foreground(null, 0, 10, null).isEmpty());
        assertTrue(
                UsageMath.foreground(
                                Collections.singletonList(m(0, "a", UsageMath.START)), 10, 10, null)
                        .isEmpty());
    }
}
