package es.foco.next.core.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.Test;

public class ReportQueueTest {
    static final LocalDate D = LocalDate.of(2026, 11, 20);

    @Test
    public void tresDiasSinRedSeEnvianLosTresEnOrden() {
        // Lo último cerrado fue el 20; el 21, 22 y 23 no hubo red; hoy es el 24.
        List<LocalDate> p = ReportQueue.pending(D, D.plusDays(4), D.minusDays(10));
        assertEquals(List.of(D.plusDays(1), D.plusDays(2), D.plusDays(3), D.plusDays(4)), p);
        LocalDate last = D;
        for (LocalDate d : p) last = ReportQueue.advance(last, d, D.plusDays(4));
        // El de hoy aún no está cerrado: se vuelve a subir mañana.
        assertEquals(D.plusDays(3), last);
        assertEquals(
                List.of(D.plusDays(4), D.plusDays(5)), ReportQueue.pending(last, D.plusDays(5), D));
    }

    @Test
    public void nadaAntesDeConectarDrive() {
        assertEquals(List.of(D), ReportQueue.pending(null, D, D));
        assertEquals(ReportQueue.MAX_DAYS, ReportQueue.pending(null, D, D.minusDays(400)).size());
    }

    @Test
    public void seEnviaUnaVezALasNueve() {
        assertEquals(30, ReportQueue.minutesUntilSend(LocalDateTime.of(2026, 11, 28, 20, 30)));
        // Pasadas las 21:00 (hoy ya se envió), el próximo es mañana a las 21:00.
        assertEquals(
                23 * 60 + 45, ReportQueue.minutesUntilSend(LocalDateTime.of(2026, 11, 28, 21, 15)));
    }
}
