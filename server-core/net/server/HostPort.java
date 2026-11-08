package net.server;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * One place for turning an admin-supplied host into something the login/channel redirect path can
 * carry.
 *
 * <p>The v83 wire format for {@code SERVER_IP} (0x0C) and {@code CHANGE_CHANNEL} (0x10) reserves
 * exactly four bytes for the channel address, which is a raw IPv4 address. Rather than break that
 * format, the bundled client and this server share a backward-compatible extension: when all four
 * bytes are zero ({@link #IPV6_MARKER}) the four bytes are followed by a length-prefixed host string
 * and the port. A stock v83 client never sees the marker, because it is only written when the
 * configured endpoint resolves to an IPv6 address.
 *
 * <p>Config values may be an IPv4 literal, an IPv6 literal (with or without brackets), a hostname,
 * and may optionally carry a {@code :port} suffix. IPv6 literals MUST be bracketed when a port is
 * appended, e.g. {@code [2001:db8::1]:7575}.
 */
public final class HostPort {
    /** Four zero bytes: "the address is in the string that follows". */
    public static final byte[] IPV6_MARKER = {0, 0, 0, 0};

    private HostPort() {
    }

    /**
     * Trim surrounding whitespace and the brackets around an IPv6 literal. A zone/scope suffix
     * ({@code fe80::1%wlan0}) is kept: it is meaningful for classification and ban matching.
     */
    public static String normalizeHost(String host) {
        if (host == null) {
            return "";
        }
        String value = host.trim();
        if (value.length() >= 2 && value.charAt(0) == '[') {
            int close = value.lastIndexOf(']');
            if (close > 0) {
                value = value.substring(1, close);
            }
        }
        return value.trim();
    }

    /** True for a numeric IPv6 literal or an IPv6 address with a scope suffix. */
    public static boolean isIpv6Literal(String host) {
        return normalizeHost(host).indexOf(':') >= 0;
    }

    /**
     * Produce a {@code host:port} endpoint that can be split again without ambiguity. IPv6 literals
     * are bracketed; hostnames and IPv4 literals are left alone.
     */
    public static String format(String host, int port) {
        String value = normalizeHost(host);
        String bracketed = isIpv6Literal(value)
                ? "[" + stripScope(value) + "]"
                : value;
        return bracketed + ":" + port;
    }

    /**
     * Split an endpoint produced by {@link #format} (or typed by hand) into {@code {host, port}}.
     * Unbracketed IPv6 literals are returned whole with an empty port.
     */
    public static String[] parse(String endpoint) {
        String value = endpoint == null ? "" : endpoint.trim();
        if (value.isEmpty()) {
            return new String[]{"", ""};
        }

        if (value.charAt(0) == '[') {
            int close = value.indexOf(']');
            if (close > 0) {
                String host = value.substring(1, close);
                String port = "";
                if (close + 1 < value.length() && value.charAt(close + 1) == ':') {
                    port = value.substring(close + 2).trim();
                }
                return new String[]{host, port};
            }
        }

        // A bare IPv6 literal always has at least two colons. One colon means "host:port"; zero
        // means a host with no port. This is why a port on an IPv6 literal requires brackets.
        int first = value.indexOf(':');
        if (first >= 0 && first == value.lastIndexOf(':')) {
            String host = value.substring(0, first).trim();
            String port = value.substring(first + 1).trim();
            if (!host.isEmpty() && isDecimal(port)) {
                return new String[]{host, port};
            }
        }

        return new String[]{value, ""};
    }

    /**
     * The textual address to put on the wire: brackets and the interface scope are removed, because
     * an interface index on the server means nothing to the client.
     */
    public static String wireHost(InetAddress address) {
        String text = address.getHostAddress();
        int scope = text.indexOf('%');
        if (scope >= 0) {
            text = text.substring(0, scope);
        }
        if (address instanceof Inet6Address) {
            return compressIpv6(text);
        }
        return text;
    }

    /**
     * True when the four-byte field has to be the escape marker: either the address is IPv6 (and
     * cannot fit), or it is the all-zero IPv4 address, which would otherwise be indistinguishable
     * from the marker and leave the client trying to read a host string out of the port bytes.
     */
    public static boolean needsIpv6Redirect(InetAddress address) {
        if (address instanceof Inet6Address) {
            return true;
        }
        byte[] bytes = address.getAddress();
        return bytes.length == 4
                && bytes[0] == 0 && bytes[1] == 0 && bytes[2] == 0 && bytes[3] == 0;
    }

    /**
     * True for a numeric IPv4 or IPv6 literal (brackets and a scope suffix are allowed). Hostnames
     * deliberately do not match: the ban commands receive either a player name or an address, and
     * a name must never be written into {@code ipbans} as if it were an address.
     */
    public static boolean isIpLiteral(String value) {
        String host = stripScope(normalizeHost(value));
        if (host.isEmpty()) {
            return false;
        }
        if (host.indexOf(':') >= 0) {
            int first = host.indexOf(':');
            return host.indexOf(':', first + 1) >= 0 && LITERAL_IPV6.matcher(host).matches();
        }
        return IPV4_TEXT.matcher(host).matches();
    }

    /**
     * Whether a client is talking to us over IPv6. Used to decide which set of channel addresses to
     * advertise: an IPv4 client must receive the original four-byte redirect (so stock v83 clients
     * keep working), while an IPv6 client should receive an address it can actually reach.
     *
     * <p>An IPv4 connection accepted on a dual-stack socket can surface as an IPv4-mapped IPv6
     * address ({@code ::ffff:192.168.1.5} or {@code 0:0:0:0:0:ffff:c0a8:105}); that is still an
     * IPv4 peer and must not be handed an IPv6-only address. Hostnames and the placeholder
     * {@code "null"} used for mock clients are treated as IPv4, the safe default.
     */
    public static boolean isIpv6Family(String host) {
        String value = stripScope(normalizeHost(host));
        if (!isIpLiteral(value)) {
            // Never send a hostname into a resolver on the login path.
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(value);
            return address instanceof Inet6Address && !isIpv4Mapped(address);
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /**
     * Resolve a configured IPv6-side channel host into a literal the redirect packet can carry.
     *
     * <p>Numeric literals are returned as-is. A hostname is resolved to its first real AAAA record:
     * {@code InetAddress.getByName} prefers IPv4 on a dual-stack JVM, which would silently turn an
     * IPv6 client's redirect back into IPv4. Returns {@code null} when the value is empty or a
     * hostname with no usable AAAA record, so the caller can fall back to the IPv4-side config.
     */
    public static String resolveIpv6Host(String host) {
        String literal = stripScope(normalizeHost(host));
        if (literal.isEmpty()) {
            return null;
        }
        if (isIpLiteral(literal)) {
            return literal;
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(literal)) {
                if (address instanceof Inet6Address && !isIpv4Mapped(address)) {
                    return wireHost(address);
                }
            }
        } catch (UnknownHostException ignored) {
            // No AAAA record; let the caller fall back to the IPv4-side address.
        }
        return null;
    }

    private static boolean isIpv4Mapped(InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return false;
        }
        byte[] bytes = address.getAddress();
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return (bytes[10] & 0xff) == 0xff && (bytes[11] & 0xff) == 0xff;
    }

    private static boolean isDecimal(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < '0' || value.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }

    private static String stripScope(String host) {
        int scope = host.indexOf('%');
        return scope < 0 ? host : host.substring(0, scope);
    }

    /** Shorten the fully expanded form returned by {@link Inet6Address#getHostAddress()}. */
    private static String compressIpv6(String text) {
        String[] groups = text.toLowerCase(Locale.ROOT).split(":", -1);
        if (groups.length != 8) {
            return text;
        }

        int bestStart = -1;
        int bestLength = 0;
        for (int i = 0; i < groups.length; ) {
            if (!"0".equals(groups[i]) && !"0000".equals(groups[i])) {
                i++;
                continue;
            }
            int start = i;
            while (i < groups.length
                    && ("0".equals(groups[i]) || "0000".equals(groups[i]))) {
                i++;
            }
            if (i - start > bestLength && i - start >= 2) {
                bestStart = start;
                bestLength = i - start;
            }
        }

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < groups.length; ) {
            if (i == bestStart) {
                out.append("::");
                i += bestLength;
                continue;
            }
            if (i > 0 && i != bestStart + bestLength) {
                out.append(':');
            }
            // Groups arrive as hex text; keep them hex (Integer.parseInt(..., 16) alone would
            // print the group in decimal, turning "2001" into "8193").
            String group = groups[i].isEmpty() ? "0" : groups[i];
            out.append(Integer.toHexString(Integer.parseInt(group, 16)));
            i++;
        }
        return out.length() == 0 ? "::" : out.toString();
    }

    private static final Pattern IPV4_TEXT = Pattern.compile("[0-9]{1,3}(?:\\.[0-9]{1,3}){3}");
    private static final Pattern LITERAL_IPV6 = Pattern.compile("[0-9A-Fa-f:.]+");
}
