package es.foco.next.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import es.foco.next.core.LeisurePolicy.Layer;
import es.foco.next.core.LeisurePolicy.Pass;
import es.foco.next.core.care.CareMode;
import org.junit.Test;

public class LeisurePolicyTest {
    @Test
    public void laNocheNoLaSaltaNada() {
        assertEquals(Pass.NONE, LeisurePolicy.pass(true, true, true, true, true));
        assertEquals(
                LeisurePolicy.Reason.NIGHT,
                LeisurePolicy.backup(true, true, false, false, Pass.WEEK_COUPON));
    }

    @Test
    public void elPaseMasEspecificoGana() {
        assertEquals(Pass.WEEK_COUPON, LeisurePolicy.pass(false, true, true, true, true));
        assertEquals(Pass.COUPON, LeisurePolicy.pass(false, true, true, false, true));
        assertEquals(Pass.FREE_HOUR, LeisurePolicy.pass(false, true, false, false, true));
        assertEquals(Pass.CARE, LeisurePolicy.pass(false, true, false, false, false));
        assertEquals(Pass.NONE, LeisurePolicy.pass(false, false, false, false, false));
    }

    @Test
    public void laHoraLibreSoloQuitaElLimite() {
        // H1: el Pomodoro, los bloqueos con nombre, las webs y el DNS siguen en la hora libre.
        assertTrue(LeisurePolicy.skips(Pass.FREE_HOUR, Layer.BUDGET));
        assertFalse(LeisurePolicy.skips(Pass.FREE_HOUR, Layer.POMODORO));
        assertFalse(LeisurePolicy.skips(Pass.FREE_HOUR, Layer.NAMED_RULES));
        assertFalse(LeisurePolicy.skips(Pass.FREE_HOUR, Layer.WEB));
        assertFalse(LeisurePolicy.skips(Pass.FREE_HOUR, Layer.DNS));
        assertFalse(LeisurePolicy.skips(Pass.FREE_HOUR, Layer.ROUTINE));
    }

    @Test
    public void elCuponNormalNoSaltaElPomodoroNiElDns() {
        assertTrue(LeisurePolicy.skips(Pass.COUPON, Layer.NAMED_RULES));
        assertTrue(LeisurePolicy.skips(Pass.COUPON, Layer.ROUTINE));
        assertFalse(LeisurePolicy.skips(Pass.COUPON, Layer.POMODORO));
        assertFalse(LeisurePolicy.skips(Pass.COUPON, Layer.WEB));
        assertFalse(LeisurePolicy.skips(Pass.COUPON, Layer.DNS));
        for (Layer l : Layer.values()) assertTrue(LeisurePolicy.skips(Pass.WEEK_COUPON, l));
        for (Layer l : Layer.values()) assertFalse(LeisurePolicy.skips(Pass.NONE, l));
    }

    @Test
    public void cuidadoMantieneLaRutina() {
        assertFalse(LeisurePolicy.skips(Pass.CARE, Layer.ROUTINE));
        assertTrue(LeisurePolicy.skips(Pass.CARE, Layer.BUDGET));
        assertFalse(LeisurePolicy.reward(Pass.CARE));
        assertTrue(LeisurePolicy.reward(Pass.COUPON));
    }

    @Test
    public void elWeekendLockCierraEnNormalYEnfoque() {
        assertTrue(LeisurePolicy.gated(CareMode.Modo.NORMAL, true));
        assertTrue(LeisurePolicy.gated(CareMode.Modo.ENFOQUE, true));
        assertFalse(LeisurePolicy.gated(CareMode.Modo.CUIDADO, true));
        assertTrue(LeisurePolicy.gated(CareMode.Modo.ENFOQUE, false));
        assertFalse(LeisurePolicy.gated(CareMode.Modo.NORMAL, false));
    }

    @Test
    public void respaldo() {
        assertNull(LeisurePolicy.backup(false, true, true, true, Pass.NONE));
        assertEquals(
                LeisurePolicy.Reason.GATE,
                LeisurePolicy.backup(true, false, true, false, Pass.NONE));
        assertNull(LeisurePolicy.backup(true, false, true, false, Pass.FREE_HOUR));
        assertEquals(
                LeisurePolicy.Reason.ROUTINE,
                LeisurePolicy.backup(true, false, true, true, Pass.FREE_HOUR));
        assertNull(LeisurePolicy.backup(true, false, true, true, Pass.COUPON));
        assertNull(LeisurePolicy.backup(true, false, false, false, Pass.NONE));
    }
}
