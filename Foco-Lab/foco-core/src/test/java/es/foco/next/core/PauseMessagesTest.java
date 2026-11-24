package es.foco.next.core;

import static org.junit.Assert.*;

import es.foco.next.core.PauseMessages.Category;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class PauseMessagesTest {
    @Test
    public void everyCategoryHasEnoughNonEmptyPhrases() {
        for (Category c : Category.values()) {
            assertTrue(c + " tiene pocas frases", PauseMessages.of(c).size() >= 12);
            for (String s : PauseMessages.of(c)) {
                assertNotNull(s);
                assertFalse(s.trim().isEmpty());
                assertEquals(s.trim(), s);
                assertTrue(s, s.length() <= 140);
            }
        }
    }

    @Test
    public void noDuplicatesAcrossCategories() {
        Set<String> seen = new HashSet<>();
        for (Category c : Category.values())
            for (String s : PauseMessages.of(c))
                assertTrue("Duplicada: " + s, seen.add(s.toLowerCase()));
    }

    @Test
    public void rotationNeverRepeatsConsecutivelyAndCoversWholePool() {
        Set<Category> all = EnumSet.allOf(Category.class);
        int n = PauseMessages.pool(all).size();
        for (long seed : new long[] {0, 1, 20_000, -7}) {
            String last = null;
            Set<String> round = new HashSet<>();
            for (int i = 0; i < n * 4; i++) {
                String now = PauseMessages.next(all, seed, i).text;
                assertNotEquals("Repetida en " + i, last, now);
                last = now;
                if (i < n) round.add(now);
            }
            assertEquals(n, round.size());
        }
    }

    @Test
    public void rotationIsDeterministicPerSeed() {
        Set<Category> all = EnumSet.allOf(Category.class);
        for (int i = 0; i < 50; i++)
            assertEquals(PauseMessages.next(all, 42, i).text, PauseMessages.next(all, 42, i).text);
        boolean differs = false;
        for (int i = 0; i < 10; i++)
            differs |=
                    !PauseMessages.next(all, 1, i).text.equals(PauseMessages.next(all, 2, i).text);
        assertTrue(differs);
    }

    @Test
    public void categoryFilterOnlyReturnsChosenCategories() {
        Set<Category> some = EnumSet.of(Category.CUERPO, Category.APRENDER);
        for (int i = 0; i < 100; i++) {
            PauseMessages.Message m = PauseMessages.next(some, 9, i);
            assertTrue(some.contains(m.category));
            assertTrue(PauseMessages.of(m.category).contains(m.text));
        }
        for (int i = 0; i < 40; i++)
            assertEquals(Category.FE, PauseMessages.next(EnumSet.of(Category.FE), 3, i).category);
    }

    @Test
    public void emptySelectionFallsBackToAnimo() {
        assertEquals(
                PauseMessages.of(Category.ANIMO).size(),
                PauseMessages.pool(EnumSet.noneOf(Category.class)).size());
        assertEquals(Category.ANIMO, PauseMessages.next(null, 5, 3).category);
        assertEquals(
                Category.ANIMO, PauseMessages.next(EnumSet.noneOf(Category.class), 5, -1).category);
    }
}
