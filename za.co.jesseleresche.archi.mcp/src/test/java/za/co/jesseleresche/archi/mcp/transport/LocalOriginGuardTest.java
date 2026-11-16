package za.co.jesseleresche.archi.mcp.transport;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.Test;

class LocalOriginGuardTest {

    static boolean ok(String host, String origin) {
        return LocalOriginGuard.isAllowed(host, origin, Set.of());
    }

    @Test
    void allowsLocalHostsWithOrWithoutPortAndNoOrigin() {
        assertTrue(ok("localhost:7432", null));
        assertTrue(ok("127.0.0.1:7432", null));
        assertTrue(ok("127.0.0.1", null));
        assertTrue(ok("[::1]:7432", null));
        assertTrue(ok("LOCALHOST:7432", null));
    }

    @Test
    void rejectsForeignOrMissingHost() {
        assertFalse(ok("evil.example.com", null));
        assertFalse(ok("evil.example.com:7432", null));
        assertFalse(ok("localhost.evil.com:7432", null));
        assertFalse(ok("127.0.0.1.nip.io:7432", null));
        assertFalse(ok(null, null));
        assertFalse(ok("", null));
    }

    @Test
    void originWhenPresentMustBeLocal() {
        assertTrue(ok("localhost:7432", "http://localhost:3000"));
        assertTrue(ok("localhost:7432", "https://127.0.0.1"));
        assertTrue(ok("localhost:7432", "http://[::1]:8080"));
        assertFalse(ok("localhost:7432", "https://example.com"));
        assertFalse(ok("localhost:7432", "http://localhost.example.com"));
        assertFalse(ok("localhost:7432", "null"));
        assertFalse(ok("localhost:7432", "file://"));
        assertFalse(ok("localhost:7432", "not a uri"));
    }

    @Test
    void extraAllowedHostsCoverTunnels() {
        Set<String> extra = LocalOriginGuard.parseAllowedHosts(" my.tunnel.dev , Other.Example ");
        assertTrue(LocalOriginGuard.isAllowed("my.tunnel.dev", "https://my.tunnel.dev", extra));
        assertTrue(LocalOriginGuard.isAllowed("other.example:443", null, extra));
        assertFalse(LocalOriginGuard.isAllowed("evil.example.com", null, extra));
        assertTrue(LocalOriginGuard.parseAllowedHosts(null).isEmpty());
    }
}
