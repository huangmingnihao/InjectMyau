package me.ksyz.accountmanager.auth.cookie;

import java.net.URI;
import java.util.Locale;

public final class StoredCookie {
    public final String domain;
    public final String path;
    public final String name;
    public final String value;
    public final boolean secure;

    public StoredCookie(String domain, String path, String name, String value, boolean secure) {
        this.domain = normalizeDomain(domain);
        this.path = normalizePath(path);
        this.name = name;
        this.value = value;
        this.secure = secure;
    }

    public boolean matches(URI uri) {
        if (uri == null || this.name == null || this.name.isEmpty()) {
            return false;
        }
        if (this.secure && !"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        host = host.toLowerCase(Locale.ROOT);
        if (!this.domainMatches(host)) {
            return false;
        }
        String requestPath = uri.getPath();
        if (requestPath == null || requestPath.isEmpty()) {
            requestPath = "/";
        }
        return requestPath.startsWith(this.path);
    }

    private boolean domainMatches(String host) {
        if (this.domain == null || this.domain.isEmpty()) {
            return true;
        }
        String normalized = this.domain.toLowerCase(Locale.ROOT);
        if (!normalized.startsWith(".")) {
            return host.equals(normalized);
        }
        String bare = normalized.substring(1);
        return host.equals(bare) || host.endsWith(normalized);
    }

    private static String normalizeDomain(String domain) {
        if (domain == null) {
            return "";
        }
        domain = domain.trim().toLowerCase(Locale.ROOT);
        if (domain.isEmpty()) {
            return "";
        }
        return !domain.startsWith(".") && domain.contains(".") ? "." + domain : domain;
    }

    private static String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }
}
