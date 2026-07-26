package cl.streambox.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.net.InetAddress;

public final class DlnaDiscoveryTest {
    @Test
    public void calculatesDirectedBroadcastForHomeNetwork() throws Exception {
        InetAddress address = InetAddress.getByName("192.168.0.42");

        assertEquals(
                "192.168.0.255",
                DlnaDiscovery.directedBroadcast(address, 24).getHostAddress()
        );
    }

    @Test
    public void ignoresIpv6AndHostOnlyAddresses() throws Exception {
        assertNull(DlnaDiscovery.directedBroadcast(
                InetAddress.getByName("2001:db8::1"),
                64
        ));
        assertNull(DlnaDiscovery.directedBroadcast(
                InetAddress.getByName("192.168.0.42"),
                32
        ));
    }
}
