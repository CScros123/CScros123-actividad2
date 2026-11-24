package es.foco.next.data;

import static org.junit.Assert.*;

import es.foco.next.core.*;
import java.util.*;
import org.json.JSONException;
import org.junit.Test;

/** Lote 5: copia de seguridad de los bloqueos (ida y vuelta, validación y clamp). */
public class RuleBackupTest {
    @Test
    public void roundTripKeepsEveryField() throws Exception {
        BlockRule r = new BlockRule();
        r.id = "a";
        r.name = "Redes";
        r.enabled = false;
        r.autoAddNew = true;
        r.packages.add("com.x");
        r.limitMinutes = 30;
        r.period = BlockRule.Period.WINDOW;
        r.windowMinutes = 90;
        r.maxPauses = 2;
        r.pauseMinutes = 10;
        r.allowlist = true;
        r.scheduleMode = BlockRule.ScheduleMode.BLOCK_IN_WINDOW;
        r.schedules.add(Schedule.of(Schedule.ALL_DAYS, 9 * 60, 17 * 60));
        r.sites.add("youtube.com");
        r.keywords.add("apuestas");
        r.blockUnsupportedBrowsers = true;
        r.blockPurchases = true;
        r.clamp();
        List<BlockRule> back =
                RuleStore.parseBackup(RuleStore.exportJson(Collections.singletonList(r)));
        assertEquals(1, back.size());
        BlockRule b = back.get(0);
        assertEquals("a", b.id);
        assertEquals("Redes", b.name);
        assertFalse(b.enabled);
        assertTrue(b.autoAddNew);
        assertEquals(r.packages, b.packages);
        assertEquals(30, b.limitMinutes);
        assertEquals(BlockRule.Period.WINDOW, b.period);
        assertEquals(90, b.windowMinutes);
        assertEquals(2, b.maxPauses);
        assertEquals(10, b.pauseMinutes);
        assertTrue(b.allowlist);
        assertEquals(BlockRule.ScheduleMode.BLOCK_IN_WINDOW, b.scheduleMode);
        assertEquals(9 * 60, b.schedules.get(0).startMinute);
        assertEquals(r.sites, b.sites);
        assertEquals(r.keywords, b.keywords);
        assertTrue(b.blockUnsupportedBrowsers);
        assertTrue(b.blockPurchases);
    }

    @Test
    public void clampsAndSkipsInvalidEntries() throws Exception {
        String json =
                "{\"format\":1,\"rules\":[{\"id\":\"x\",\"name\":\"  \",\"limitMinutes\":99999,\"maxPauses\":40,\"pauseMinutes\":0,\"scheduleMode\":\"NOPE\"},{\"name\":\"sin id\"},{\"id\":\"x\",\"name\":\"repetido\"}]}";
        List<BlockRule> in = RuleStore.parseBackup(json);
        assertEquals(1, in.size());
        BlockRule r = in.get(0);
        assertEquals("Bloqueo", r.name);
        assertEquals(24 * 60, r.limitMinutes);
        assertEquals(BlockRule.MAX_PAUSES, r.maxPauses);
        assertEquals(1, r.pauseMinutes);
        assertEquals(BlockRule.ScheduleMode.ALWAYS_LIMIT, r.scheduleMode);
        assertFalse(r.blockPurchases);
    }

    @Test
    public void sectionsRoundTripAndOldFilesStillLoad() throws Exception {
        BlockRule r = new BlockRule();
        r.id = "s";
        r.name = "Sin Shorts ni Reels";
        r.sections.add("youtube_shorts");
        r.sections.add("instagram_reels");
        r.clamp();
        BlockRule b =
                RuleStore.parseBackup(RuleStore.exportJson(Collections.singletonList(r))).get(0);
        assertEquals(Arrays.asList("youtube_shorts", "instagram_reels"), b.sections);
        // Archivo antiguo sin "sections" y otro con ids desconocidos: se leen igual.
        BlockRule old =
                RuleStore.parseBackup(
                                "{\"format\":1,\"rules\":[{\"id\":\"o\",\"name\":\"Redes\",\"packages\":[\"com.x\"]}]}")
                        .get(0);
        assertTrue(old.sections.isEmpty());
        BlockRule odd =
                RuleStore.parseBackup(
                                "{\"format\":1,\"rules\":[{\"id\":\"o\",\"sections\":[\"futuro\",\"facebook_reels\",\"facebook_reels\"]}]}")
                        .get(0);
        assertEquals(Collections.singletonList("facebook_reels"), odd.sections);
        // Sin secciones no se escribe la clave (archivo igual que en la 1.1).
        BlockRule plain = new BlockRule();
        plain.id = "p";
        plain.clamp();
        assertFalse(RuleStore.exportJson(Collections.singletonList(plain)).contains("sections"));
    }

    @Test
    public void rejectsOtherFiles() {
        for (String bad :
                new String[] {
                    "no es json", "{}", "{\"format\":2,\"rules\":[]}", "{\"config\":{}}", null
                }) {
            try {
                RuleStore.parseBackup(bad);
                fail(bad);
            } catch (JSONException expected) {
            }
        }
    }
}
