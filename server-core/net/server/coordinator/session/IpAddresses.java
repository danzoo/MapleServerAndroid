package net.server.coordinator.session;

import net.server.HostPort;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Classifies a client's remote address as loopback or LAN so the login server can advertise the
 * right channel endpoint (LOCALHOST / LANHOST / HOST).
 *
 * <p>The original implementation only understood dotted-quad IPv4 text. An IPv6 client - including
 * one arriving as an IPv4-mapped address on a dual-stack socket - failed both checks and was handed
 * the public HOST, which is usually unreachable from inside the LAN.
 */
public class IpAddresses {
    private static final List<Pattern> LOCAL_ADDRESS_PATTERNS = loadLocalAddressPatterns();
    private static final Pattern IPV4_TEXT = Pattern.compile("[0-9]{1,3}(?:\\.[0-9]{1,3}){3}");
    private static final Pattern IPV6_TEXT = Pattern.compile("[0-9A-Fa-f:.]+");

    private static List<Pattern> loadLocalAddressPatterns() {
        return Stream.of("10\\.", "192\\.168\\.", "172\\.(1[6-9]|2[0-9]|3[0-1])\\.")
                .map(Pattern::compile)
                .collect(Collectors.toList());
    }

    public static boolean isLocalAddress(String inetAddress) {
        String host = HostPort.normalizeHost(inetAddress);
        if (host.isEmpty()) {
            return false;
        }

        InetAddress address = parseNumeric(host);
        if (address != null) {
            // Covers 127.0.0.0/8, ::1 and ::ffff:127.0.0.1 (Java unwraps IPv4-mapped addresses).
            return address.isLoopbackAddress();
        }
        return host.startsWith("127.") || host.equalsIgnoreCase("localhost");
    }

    public static boolean isLanAddress(String inetAddress) {
        String host = HostPort.normalizeHost(inetAddress);
        if (host.isEmpty()) {
            return false;
        }

        InetAddress address = parseNumeric(host);
        if (address instanceof Inet6Address) {
            byte[] bytes = address.getAddress();
            // fc00::/7 unique-local and fe80::/10 link-local. InetAddress.isSiteLocalAddress() is
            // not usable here: on IPv6 it only recognises the deprecated fec0::/10 site-local range.
            boolean uniqueLocal = (bytes[0] & 0xfe) == 0xfc;
            boolean linkLocal = (bytes[0] & 0xff) == 0xfe && (bytes[1] & 0xc0) == 0x80;
            return uniqueLocal || linkLocal || address.isSiteLocalAddress();
        }
        if (address != null) {
            return address.isSiteLocalAddress() || address.isLinkLocalAddress();
        }

        return LOCAL_ADDRESS_PATTERNS.stream()
                .anyMatch(pattern -> matchesPattern(pattern, host));
    }

    /**
     * Parse a numeric IPv4/IPv6 literal without ever triggering a DNS lookup. The login path calls
     * this on every connection, and a hostname here must not turn into a blocking resolver call.
     */
    private static InetAddress parseNumeric(String host) {
        String value = host;
        int scope = value.indexOf('%');
        if (scope >= 0) {
            value = value.substring(0, scope);
        }

        // A numeric IPv6 literal always has at least two colons. Requiring that keeps strings such as
        // "dead:beef" (which is a hostname, not an address) out of the resolver entirely.
        int firstColon = value.indexOf(':');
        boolean ipv6 = firstColon >= 0
                && value.indexOf(':', firstColon + 1) >= 0
                && IPV6_TEXT.matcher(value).matches();
        if (!ipv6 && !IPV4_TEXT.matcher(value).matches()) {
            return null;
        }
        try {
            return InetAddress.getByName(value);
        } catch (Exception ignored) {
            // Fall through to the text patterns.
        }
        return null;
    }

    private static boolean matchesPattern(Pattern pattern, String searchTerm) {
        return pattern.matcher(searchTerm).find();
    }
}
