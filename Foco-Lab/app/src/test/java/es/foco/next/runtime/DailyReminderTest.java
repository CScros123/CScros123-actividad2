package es.foco.next.runtime;

import static org.junit.Assert.*;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.os.*;
import java.time.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public class DailyReminderTest {
    private Context c;
    private AlarmManager alarms;
    private NotificationManager notifications;

    @Before
    public void init() {
        c = RuntimeEnvironment.getApplication();
        c.getSharedPreferences("foco-daily-reminder", 0).edit().clear().commit();
        Shadows.shadowOf((Application) c).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        alarms = c.getSystemService(AlarmManager.class);
        notifications = c.getSystemService(NotificationManager.class);
    }

    @Test
    public void reminderIsOffByDefaultAndDoesNotSchedule() {
        DailyReminder.ensure(c);
        assertFalse(DailyReminder.enabled(c));
        assertEquals(0, Shadows.shadowOf(alarms).getScheduledAlarms().size());
    }

    /** v28: la nota del día salió; un aviso encendido de antes se apaga solo al abrir Foco. */
    @Test
    public void oldReminderIsTurnedOffOnEnsure() {
        DailyReminder.configure(c, true, 1200);
        assertTrue(DailyReminder.enabled(c));
        DailyReminder.ensure(c);
        assertFalse(DailyReminder.enabled(c));
        assertEquals(0, Shadows.shadowOf(alarms).getScheduledAlarms().size());
    }
}
