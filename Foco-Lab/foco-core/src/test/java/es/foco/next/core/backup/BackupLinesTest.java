package es.foco.next.core.backup;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class BackupLinesTest {
    @Test
    public void ochoVariantesSinRepetirEnTresDias() {
        assertTrue(BackupLines.DONE.length >= 8);
        List<Integer> used = new ArrayList<>();
        for (int day = 0; day < 60; day++) {
            int i = BackupLines.pick(used, day % 2 == 0 ? 5 : day);
            int from = Math.max(0, used.size() - BackupLines.NO_REPEAT_DAYS);
            assertFalse(used.subList(from, used.size()).contains(i));
            used.add(i);
        }
    }

    @Test
    public void sinCulpa() {
        for (String s : BackupLines.DONE) assertFalse(s.toLowerCase().contains("deberías"));
        assertTrue(BackupLines.FAILED.contains("lo intento más tarde"));
    }
}
