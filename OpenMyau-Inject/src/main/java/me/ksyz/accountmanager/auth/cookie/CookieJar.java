package me.ksyz.accountmanager.auth.cookie;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CookieJar {
    private static final Gson GSON = new Gson();
    private final List<StoredCookie> cookies = new ArrayList<StoredCookie>();

    public void put(StoredCookie cookie) {
        if (cookie == null || isBlank(cookie.name) || isBlank(cookie.value)) {
            return;
        }
        if ("Disabled".equalsIgnoreCase(cookie.value)) {
            return;
        }
        for (int i = 0; i < this.cookies.size(); i++) {
            if (sameSlot(this.cookies.get(i), cookie)) {
                this.cookies.set(i, cookie);
                return;
            }
        }
        this.cookies.add(cookie);
    }

    public void put(String domain, String path, String name, String value, boolean secure) {
        this.put(new StoredCookie(domain, path, name, value, secure));
    }

    public boolean isEmpty() {
        return this.cookies.isEmpty();
    }

    public int size() {
        return this.cookies.size();
    }

    public String buildCookieHeader(URI uri, List<String> preferredOrder) {
        List<StoredCookie> matching = new ArrayList<StoredCookie>();
        for (StoredCookie cookie : this.cookies) {
            if (cookie.matches(uri)) {
                matching.add(cookie);
            }
        }
        if (matching.isEmpty()) {
            return "";
        }
        List<String> orderedNames = new ArrayList<String>();
        if (preferredOrder != null) {
            orderedNames.addAll(preferredOrder);
        }
        for (StoredCookie cookie : matching) {
            if (!orderedNames.contains(cookie.name)) {
                orderedNames.add(cookie.name);
            }
        }
        Map<String, String> values = new LinkedHashMap<String, String>();
        for (StoredCookie cookie : matching) {
            values.put(cookie.name, cookie.value);
        }
        StringBuilder header = new StringBuilder();
        for (String name : orderedNames) {
            if (values.containsKey(name)) {
                if (header.length() != 0) {
                    header.append("; ");
                }
                header.append(name).append('=').append(values.get(name));
            }
        }
        return header.toString();
    }

    public String buildCookieHeader(URI uri) {
        return this.buildCookieHeader(uri, null);
    }

    public String findMinecraftNetValue(String cookieName) {
        for (int i = this.cookies.size() - 1; i >= 0; i--) {
            StoredCookie cookie = this.cookies.get(i);
            if (cookieName.equals(cookie.name)) {
                String domain = cookie.domain == null ? "" : cookie.domain.toLowerCase(Locale.ROOT);
                if (domain.contains("minecraft.net")) {
                    return cookie.value;
                }
            }
        }
        return null;
    }

    public boolean hasRequiredAuthCookies() {
        for (StoredCookie cookie : this.cookies) {
            if ("__Host-MSAAUTH".equals(cookie.name) || "__Host-MSAAUTHP".equals(cookie.name)
                    || "JSH".equals(cookie.name) || "JSHP".equals(cookie.name)) {
                return true;
            }
        }
        return false;
    }

    public String serialize() {
        JsonObject root = new JsonObject();
        root.addProperty("v", 2);
        JsonArray array = new JsonArray();
        for (StoredCookie cookie : this.cookies) {
            JsonObject entry = new JsonObject();
            entry.addProperty("domain", cookie.domain);
            entry.addProperty("path", cookie.path);
            entry.addProperty("name", cookie.name);
            entry.addProperty("value", cookie.value);
            entry.addProperty("secure", cookie.secure);
            array.add(entry);
        }
        root.add("cookies", array);
        return GSON.toJson(root);
    }

    public static CookieJar deserialize(String serialized) {
        CookieJar jar = new CookieJar();
        if (isBlank(serialized)) {
            return jar;
        }
        try {
            JsonElement rootElement = new JsonParser().parse(serialized);
            if (!rootElement.isJsonObject()) {
                return jar;
            }
            JsonObject root = rootElement.getAsJsonObject();
            if (root.has("cookies") && root.get("cookies").isJsonArray()) {
                for (JsonElement element : root.getAsJsonArray("cookies")) {
                    if (element.isJsonObject()) {
                        JsonObject entry = element.getAsJsonObject();
                        String name = getString(entry, "name");
                        String value = getString(entry, "value");
                        if (!isBlank(name) && !isBlank(value)) {
                            jar.put(getString(entry, "domain"), getString(entry, "path"), name, value,
                                    entry.has("secure") && entry.get("secure").getAsBoolean());
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return jar;
    }

    public static boolean isRelevantDomain(String domain) {
        if (domain == null || domain.isEmpty()) {
            return true;
        }
        domain = domain.toLowerCase(Locale.ROOT);
        return domain.contains("live.com") || domain.contains("microsoftonline.com") || domain.contains("microsoft.com")
                || domain.contains("xboxlive.com") || domain.contains("minecraft.net") || domain.contains("mojang.com");
    }

    static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean sameSlot(StoredCookie left, StoredCookie right) {
        return eq(left.domain, right.domain) && eq(left.path, right.path) && eq(left.name, right.name);
    }

    private static boolean eq(String left, String right) {
        return left != null ? left.equals(right) : (right == null || right.isEmpty());
    }

    private static String getString(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }
}
