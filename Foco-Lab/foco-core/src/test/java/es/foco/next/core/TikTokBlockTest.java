package es.foco.next.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class TikTokBlockTest {
    @Test
    public void turningOnBlocksTikTokCompletelyWithoutPauses() {
        BlockRule r = TikTokBlock.toggle(null, true, "t1");
        assertTrue(TikTokBlock.on(r));
        assertEquals(0, r.limitMinutes);
        assertEquals(0, r.maxPauses);
        assertTrue(r.packages.containsAll(TikTokBlock.PACKAGES));
        assertEquals(TikTokBlock.RULE_NAME, r.name);
    }

    @Test
    public void turningOffIsLooseningForStrictMode() {
        BlockRule on = TikTokBlock.toggle(null, true, "t1"),
                off = TikTokBlock.toggle(on, false, "t1");
        assertFalse(TikTokBlock.on(off));
        assertNotNull(RuleChange.reason(on, off));
        assertNull(RuleChange.reason(off, TikTokBlock.toggle(off, true, "t1")));
    }
}
