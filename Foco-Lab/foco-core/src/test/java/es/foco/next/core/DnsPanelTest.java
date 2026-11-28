package es.foco.next.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** v37: NextDNS se cambia solo desde otro dispositivo. */
public class DnsPanelTest {
    @Test
    public void laWebDeNextDnsSeCierra() {
        assertTrue(Categories.dnsPanelDomain("my.nextdns.io"));
        assertTrue(Categories.dnsPanelDomain("https://my.nextdns.io/abc123/denylist"));
        assertTrue(Categories.dnsPanelDomain("nextdns.io"));
        assertTrue(Categories.dnsPanelDomain("help.nextdns.io"));
        assertFalse(Categories.dnsPanelDomain("google.com"));
        assertFalse(Categories.dnsPanelDomain("notnextdns.io"));
        assertFalse(Categories.dnsPanelDomain(null));
    }

    @Test
    public void laAppDeNextDnsSeCierra() {
        assertTrue(Categories.dnsPanelApp("io.nextdns.NextDNS"));
        assertFalse(Categories.dnsPanelApp("es.foco.next"));
        assertFalse(Categories.dnsPanelApp(null));
    }
}
