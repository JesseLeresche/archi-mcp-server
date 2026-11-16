package za.co.jesseleresche.archi.mcp.transport;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * DNS-rebinding / cross-site guard for the MCP endpoints: the server binds to 127.0.0.1, but a
 * browser page can still reach it, so requests must name a local {@code Host} and, when an
 * {@code Origin} is sent, a local origin. Extra host names (e.g. a tunnel used for Copilot
 * Studio) can be allowed with {@code -Darchi.mcp.allowed.hosts=host1,host2}.
 */
public final class LocalOriginGuard {

    public static final String ALLOWED_HOSTS_PROPERTY = "archi.mcp.allowed.hosts";

    private static final Set<String> LOCAL = Set.of("localhost", "127.0.0.1", "[::1]", "::1");

    private LocalOriginGuard() {
    }

    /** Pure check, unit-tested: host header (with optional port) and optional Origin header. */
    static boolean isAllowed(String host, String origin, Set<String> extraHosts) {
        if (host == null || !allowedHost(stripPort(host), extraHosts)) {
            return false;
        }
        if (origin == null) {
            return true;
        }
        try {
            URI u = new URI(origin);
            String scheme = u.getScheme();
            String h = u.getHost();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && h != null && allowedHost(h.toLowerCase(Locale.ROOT), extraHosts);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean allowedHost(String h, Set<String> extraHosts) {
        return LOCAL.contains(h) || extraHosts.contains(h);
    }

    private static String stripPort(String host) {
        String h = host.trim().toLowerCase(Locale.ROOT);
        if (h.startsWith("[")) {
            int end = h.indexOf(']');
            return end < 0 ? h : h.substring(0, end + 1);
        }
        int colon = h.indexOf(':');
        return colon < 0 ? h : h.substring(0, colon);
    }

    static Set<String> parseAllowedHosts(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Returns true if the request may proceed; otherwise sends 403 and returns false. */
    public static boolean check(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Set<String> extra = parseAllowedHosts(System.getProperty(ALLOWED_HOSTS_PROPERTY));
        if (isAllowed(request.getHeader("Host"), request.getHeader("Origin"), extra)) {
            return true;
        }
        response.sendError(HttpServletResponse.SC_FORBIDDEN,
                "Host/Origin not allowed; the Archi MCP server only accepts local requests (see "
                        + ALLOWED_HOSTS_PROPERTY + ")");
        return false;
    }
}
