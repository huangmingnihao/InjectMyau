package me.ksyz.accountmanager.auth.cookie;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.ksyz.accountmanager.AccountManager;
import me.ksyz.accountmanager.auth.Account;
import me.ksyz.accountmanager.auth.MicrosoftAuth;
import me.ksyz.accountmanager.auth.SessionManager;
import net.minecraft.util.Session;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class CookieAuth {
    public static final String MARKER = "cookie";

    private static final Executor DIRECT = new Executor() {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    };

    private static final List<String> COOKIE_ORDER_JSHP = Arrays.asList(
            "__Host-MSAAUTH", "__Host-MSAAUTHP", "JSHP", "JSH",
            "MSPAuth", "MSPBack", "MSPProf", "MSPRequ", "MSPSoftVis", "MSPOK", "MSPShared", "MSPPre", "MSPCID",
            "MSPOAuthVis", "AMCSecAuth", "NAP", "ANON", "OParams", "PPLState", "WLSSC", "uaid", "pres", "LOpt");
    private static final List<String> COOKIE_ORDER_JSH = Arrays.asList(
            "__Host-MSAAUTH", "__Host-MSAAUTHP", "JSH", "JSHP",
            "MSPAuth", "MSPBack", "MSPProf", "MSPRequ", "MSPSoftVis", "MSPOK", "MSPShared", "MSPPre", "MSPCID",
            "MSPOAuthVis", "AMCSecAuth", "NAP", "ANON", "OParams", "PPLState", "WLSSC", "uaid", "pres", "LOpt");

    private static final String[] OAUTH_URLS = {
            "https://login.live.com/oauth20_authorize.srf?redirect_uri=https://sisu.xboxlive.com/connect/oauth/XboxLive&response_type=token&client_id=000000004420578E&scope=XboxLive.Signin%20XboxLive.offline_access&prompt=none",
            "https://login.live.com/oauth20_authorize.srf?client_id=00000000402b5328&redirect_uri=https%3A%2F%2Flogin.live.com%2Foauth20_desktop.srf&response_type=token&scope=service%3A%3Auser.auth.xboxlive.com%3A%3AMBI_SSL&prompt=none"
    };

    private CookieAuth() {
    }

    public static CookieJar parseFile(File cookieFile) throws IOException {
        return parseContent(readFile(cookieFile));
    }

    public static CookieJar parseContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return new CookieJar();
        }
        if (content.trim().startsWith("[") || content.trim().startsWith("{")) {
            CookieJar json = parseJsonCookies(content);
            if (!json.isEmpty()) {
                return json;
            }
        }
        CookieJar netscape = parseNetscapeCookies(content);
        return !netscape.isEmpty() ? netscape : parseLooseCookies(content);
    }

    public static CompletableFuture<Void> addAccount(final CookieJar jar, Executor executor, final Consumer<String> status) {
        return CompletableFuture.supplyAsync(new java.util.function.Supplier<Void>() {
            @Override
            public Void get() {
                try {
                    verify(jar, status);
                    Result result = authenticate(jar, status);
                    Account account = new Account(
                            result.cookies, result.mcToken, result.session.getUsername(), MARKER, ""
                    );
                    for (Account existing : AccountManager.accounts) {
                        if (account.getUsername().equals(existing.getUsername())) {
                            account.setUnban(existing.getUnban());
                            break;
                        }
                    }
                    AccountManager.accounts.add(account);
                    AccountManager.save();
                    SessionManager.set(result.session);
                    return null;
                } catch (CompletionException e) {
                    throw e;
                } catch (Exception e) {
                    throw new CompletionException("Cookie login failed!", e);
                }
            }
        }, executor);
    }

    public static CompletableFuture<Void> relogin(final Account account, Executor executor, final Consumer<String> status) {
        return CompletableFuture.supplyAsync(new java.util.function.Supplier<Void>() {
            @Override
            public Void get() {
                try {
                    status.accept("&7Trying the stored Minecraft token...&r");
                    Session cached = tryStoredToken(account.getAccessToken());
                    if (cached != null) {
                        account.setUsername(cached.getUsername());
                        AccountManager.save();
                        SessionManager.set(cached);
                        return null;
                    }
                    CookieJar jar = CookieJar.deserialize(account.getRefreshToken());
                    verify(jar, status);
                    Result result = authenticate(jar, status);
                    account.setRefreshToken(result.cookies);
                    account.setAccessToken(result.mcToken);
                    account.setUsername(result.session.getUsername());
                    AccountManager.save();
                    SessionManager.set(result.session);
                    return null;
                } catch (CompletionException e) {
                    throw e;
                } catch (Exception e) {
                    throw new CompletionException("Cookie login failed!", e);
                }
            }
        }, executor);
    }

    private static void verify(CookieJar jar, Consumer<String> status) throws IOException {
        if (jar.isEmpty()) {
            throw new IOException("No usable Microsoft cookies found");
        }
        if (!jar.hasRequiredAuthCookies()) {
            throw new IOException("Missing auth cookies (need __Host-MSAAUTH, JSH or JSHP)");
        }
        status.accept("&7Read " + jar.size() + " cookies&r");
    }

    private static Session tryStoredToken(String mcToken) {
        if (CookieJar.isBlank(mcToken)) {
            return null;
        }
        try {
            return MicrosoftAuth.login(mcToken, DIRECT).join();
        } catch (Exception e) {
            return null;
        }
    }

    private static Result authenticate(CookieJar jar, Consumer<String> status) throws Exception {
        Exception lastError = null;
        try {
            status.accept("&7Authenticating with Microsoft...&r");
            String msAccessToken = acquireMicrosoftAccessToken(jar);
            if (msAccessToken != null) {
                return finishMicrosoftTokenLogin(jar, msAccessToken, status);
            }
        } catch (Exception e) {
            lastError = e;
        }
        try {
            status.accept("&7Falling back to minecraft.net...&r");
            String mcAccessToken = MinecraftNetAuth.loginForMinecraftToken(jar);
            if (mcAccessToken != null) {
                status.accept("&7Fetching your Minecraft profile...&r");
                Session session = MicrosoftAuth.login(mcAccessToken, DIRECT).join();
                return new Result(session, mcAccessToken, jar.serialize());
            }
        } catch (Exception e) {
            lastError = e;
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new IOException("Cookies did not authenticate (they may be expired)");
    }

    private static String acquireMicrosoftAccessToken(CookieJar jar) throws Exception {
        CookieHttpClient client = new CookieHttpClient(jar);
        List<List<String>> orderings = new ArrayList<List<String>>();
        orderings.add(COOKIE_ORDER_JSHP);
        orderings.add(COOKIE_ORDER_JSH);
        Exception lastError = null;
        for (String oauthUrl : OAUTH_URLS) {
            for (List<String> ordering : orderings) {
                try {
                    String token = client.followOAuthRedirects(oauthUrl, 12, ordering);
                    if (token != null) {
                        return token;
                    }
                } catch (Exception e) {
                    lastError = e;
                }
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        return null;
    }

    private static Result finishMicrosoftTokenLogin(CookieJar jar, String msAccessToken, Consumer<String> status) throws Exception {
        status.accept("&7Acquiring Xbox access token...&r");
        Map<String, String> xbl = acquireXboxToken(msAccessToken);
        status.accept("&7Acquiring Xbox XSTS token...&r");
        Map<String, String> xsts = MicrosoftAuth.acquireXboxXstsToken(xbl.get("Token"), DIRECT).join();
        status.accept("&7Acquiring Minecraft access token...&r");
        String mcToken = MicrosoftAuth.acquireMCAccessToken(xsts.get("Token"), xsts.get("uhs"), DIRECT).join();
        status.accept("&7Fetching your Minecraft profile...&r");
        Session session = MicrosoftAuth.login(mcToken, DIRECT).join();
        return new Result(session, mcToken, jar.serialize());
    }

    private static Map<String, String> acquireXboxToken(String msAccessToken) throws Exception {
        Exception lastError = null;
        for (String prefix : new String[]{"t=", "d=", ""}) {
            CloseableHttpClient client = CookieHttpClient.createClient(true);
            try {
                HttpPost request = new HttpPost(URI.create("https://user.auth.xboxlive.com/user/authenticate"));
                JsonObject entity = new JsonObject();
                JsonObject properties = new JsonObject();
                properties.addProperty("AuthMethod", "RPS");
                properties.addProperty("SiteName", "user.auth.xboxlive.com");
                properties.addProperty("RpsTicket", prefix + msAccessToken);
                entity.add("Properties", properties);
                entity.addProperty("RelyingParty", "http://auth.xboxlive.com");
                entity.addProperty("TokenType", "JWT");
                request.setConfig(CookieHttpClient.REQUEST_CONFIG);
                request.setHeader("Content-Type", "application/json");
                request.setHeader("X-Xbl-Contract-Version", "0");
                request.setEntity(new StringEntity(entity.toString(), StandardCharsets.UTF_8));
                HttpResponse response = client.execute(request);
                String body = EntityUtils.toString(response.getEntity());
                if (response.getStatusLine().getStatusCode() != 200) {
                    throw new IOException("Xbox Live authentication failed ("
                            + response.getStatusLine().getStatusCode() + ")");
                }
                JsonObject json = new JsonParser().parse(body).getAsJsonObject();
                Map<String, String> result = new HashMap<String, String>();
                result.put("Token", json.get("Token").getAsString());
                result.put("uhs", json.getAsJsonObject("DisplayClaims")
                        .getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString());
                return result;
            } catch (Exception e) {
                lastError = e;
            } finally {
                try {
                    client.close();
                } catch (Exception ignored) {
                }
            }
        }
        throw lastError != null ? lastError : new IOException("Unable to acquire Xbox Live access token!");
    }

    private static String readFile(File cookieFile) throws IOException {
        StringBuilder builder = new StringBuilder();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(cookieFile), StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        } finally {
            try {
                reader.close();
            } catch (Exception ignored) {
            }
        }
        return builder.toString();
    }

    private static CookieJar parseJsonCookies(String content) {
        CookieJar jar = new CookieJar();
        try {
            JsonElement root = new JsonParser().parse(content);
            JsonArray array;
            if (root.isJsonArray()) {
                array = root.getAsJsonArray();
            } else if (root.isJsonObject() && root.getAsJsonObject().has("cookies")) {
                array = root.getAsJsonObject().getAsJsonArray("cookies");
            } else {
                return jar;
            }
            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject object = element.getAsJsonObject();
                if (!object.has("name") || !object.has("value")) {
                    continue;
                }
                if (object.has("expirationDate")) {
                    double expiration = object.get("expirationDate").getAsDouble();
                    if (expiration > 0.0 && expiration < System.currentTimeMillis() / 1000.0) {
                        continue;
                    }
                }
                String domain = object.has("domain") ? object.get("domain").getAsString()
                        : object.has("host") ? object.get("host").getAsString() : "";
                String path = object.has("path") ? object.get("path").getAsString() : "/";
                String name = object.get("name").getAsString().trim();
                String value = object.get("value").getAsString().trim();
                boolean secure = !object.has("secure") || object.get("secure").getAsBoolean();
                if (CookieJar.isRelevantDomain(domain) && !value.isEmpty()) {
                    jar.put(domain, path, name, value, secure);
                }
            }
        } catch (Exception ignored) {
        }
        return jar;
    }

    private static CookieJar parseNetscapeCookies(String content) {
        CookieJar jar = new CookieJar();
        for (String line : content.split("\\r?\\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\t", 7);
            if (parts.length >= 7) {
                String domain = parts[0].trim();
                String path = parts[2].trim();
                String name = parts[5].trim();
                String value = parts[6].trim();
                boolean secure = "TRUE".equalsIgnoreCase(parts[3].trim());
                if (CookieJar.isRelevantDomain(domain) && !value.isEmpty()) {
                    jar.put(domain, path, name, value, secure);
                }
            }
        }
        return jar;
    }

    private static CookieJar parseLooseCookies(String content) {
        CookieJar jar = new CookieJar();
        String normalized = content.replace("\n", "").replace("\r", "");
        for (String segment : normalized.split(";")) {
            segment = segment.trim();
            int equals = segment.indexOf('=');
            if (equals > 0) {
                String name = segment.substring(0, equals).trim();
                String value = segment.substring(equals + 1).trim();
                if (!value.isEmpty()) {
                    jar.put("", "/", name, value, true);
                }
            }
        }
        if (jar.isEmpty()) {
            for (String line : content.split("\\r?\\n")) {
                line = line.trim();
                int equals = line.indexOf('=');
                if (equals > 0) {
                    String name = line.substring(0, equals).trim();
                    String value = line.substring(equals + 1).trim();
                    if (!value.isEmpty()) {
                        jar.put("", "/", name, value, true);
                    }
                }
            }
        }
        return jar;
    }

    private static final class Result {
        private final Session session;
        private final String mcToken;
        private final String cookies;

        private Result(Session session, String mcToken, String cookies) {
            this.session = session;
            this.mcToken = mcToken;
            this.cookies = cookies;
        }
    }
}
