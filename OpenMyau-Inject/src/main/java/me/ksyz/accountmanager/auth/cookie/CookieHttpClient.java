package me.ksyz.accountmanager.auth.cookie;

import me.ksyz.accountmanager.utils.SSLUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.BrowserCompatHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.List;
import java.util.Locale;

public final class CookieHttpClient {
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36";
    static final RequestConfig REQUEST_CONFIG = RequestConfig
            .custom()
            .setConnectionRequestTimeout(30_000)
            .setConnectTimeout(30_000)
            .setSocketTimeout(30_000)
            .build();

    private final CookieJar jar;

    public CookieHttpClient(CookieJar jar) {
        this.jar = jar;
    }

    static CloseableHttpClient createClient(boolean followRedirects) {
        try {
            SSLConnectionSocketFactory factory = new SSLConnectionSocketFactory(
                    SSLUtils.getSSLContext().getSocketFactory(),
                    new String[]{"TLSv1.2"},
                    null,
                    new BrowserCompatHostnameVerifier()
            );
            HttpClientBuilder builder = HttpClientBuilder.create()
                    .setSSLSocketFactory(factory)
                    .disableCookieManagement();
            if (!followRedirects) {
                builder.disableRedirectHandling();
            }
            return builder.build();
        } catch (Exception ignored) {
        }
        return HttpClients.createDefault();
    }

    public void followRedirects(String startUrl, int maxRedirects) throws Exception {
        CloseableHttpClient client = createClient(false);
        try {
            String currentUrl = startUrl;
            for (int hop = 0; hop < maxRedirects; hop++) {
                URI uri = toUri(currentUrl);
                HttpResponse response = this.request(client, uri, null);
                int statusCode = response.getStatusLine().getStatusCode();
                this.mergeResponseCookies(response, uri);
                String location = firstHeader(response, "Location");
                EntityUtils.consumeQuietly(response.getEntity());
                if (location == null) {
                    if (statusCode < 200 || statusCode >= 300) {
                        throw new IOException("Request failed (" + statusCode + ") at " + currentUrl);
                    }
                    return;
                }
                currentUrl = resolve(uri, location);
            }
            throw new IOException("Too many redirects while requesting " + startUrl);
        } finally {
            closeQuietly(client);
        }
    }

    public String followOAuthRedirects(String startUrl, int maxRedirects, List<String> preferredOrder) throws Exception {
        CloseableHttpClient client = createClient(false);
        try {
            String currentUrl = startUrl;
            for (int hop = 0; hop < maxRedirects; hop++) {
                URI uri = toUri(currentUrl);
                HttpResponse response = this.request(client, uri, preferredOrder);
                int statusCode = response.getStatusLine().getStatusCode();
                this.mergeResponseCookies(response, uri);
                String location = firstHeader(response, "Location");
                EntityUtils.consumeQuietly(response.getEntity());
                if (location == null) {
                    return null;
                }
                String oauthError = extractOAuthError(location);
                if (oauthError != null) {
                    throw new IOException(oauthError);
                }
                String token = extractAccessToken(location);
                if (token != null) {
                    return token;
                }
                if (statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307) {
                    currentUrl = resolve(uri, location);
                    continue;
                }
                return null;
            }
            return null;
        } finally {
            closeQuietly(client);
        }
    }

    private HttpResponse request(CloseableHttpClient client, URI uri, List<String> preferredOrder) throws IOException {
        HttpGet get = new HttpGet(uri);
        get.setConfig(REQUEST_CONFIG);
        get.setHeader("User-Agent", USER_AGENT);
        get.setHeader("Accept", preferredOrder == null
                ? "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                : "*/*");
        get.setHeader("Accept-Language", "en-US,en;q=0.9");
        String cookieHeader = this.jar.buildCookieHeader(uri, preferredOrder);
        if (!CookieJar.isBlank(cookieHeader)) {
            get.setHeader("Cookie", cookieHeader);
        }
        return client.execute(get);
    }

    private void mergeResponseCookies(HttpResponse response, URI requestUri) {
        Header[] headers = response.getHeaders("Set-Cookie");
        if (headers == null) {
            return;
        }
        for (Header header : headers) {
            this.parseSetCookie(header.getValue(), requestUri);
        }
    }

    private void parseSetCookie(String headerValue, URI requestUri) {
        if (CookieJar.isBlank(headerValue)) {
            return;
        }
        String[] parts = headerValue.split(";", -1);
        if (parts.length == 0) {
            return;
        }
        String nameValue = parts[0].trim();
        int equals = nameValue.indexOf('=');
        if (equals <= 0) {
            return;
        }
        String name = nameValue.substring(0, equals).trim();
        String value = nameValue.substring(equals + 1).trim();
        String domain = requestUri.getHost();
        String path = "/";
        boolean secure = false;
        for (int i = 1; i < parts.length; i++) {
            String attribute = parts[i].trim();
            if (attribute.isEmpty()) {
                continue;
            }
            int attrEquals = attribute.indexOf('=');
            String key = attrEquals > 0
                    ? attribute.substring(0, attrEquals).trim().toLowerCase(Locale.ROOT)
                    : attribute.toLowerCase(Locale.ROOT);
            String attrValue = attrEquals > 0 ? attribute.substring(attrEquals + 1).trim() : "";
            if ("domain".equals(key) && !attrValue.isEmpty()) {
                domain = attrValue;
            } else if ("path".equals(key) && !attrValue.isEmpty()) {
                path = attrValue;
            } else if ("secure".equals(key)) {
                secure = true;
            }
        }
        this.jar.put(domain, path, name, value, secure);
    }

    private static String firstHeader(HttpResponse response, String name) {
        Header header = response.getFirstHeader(name);
        return header == null ? null : header.getValue();
    }

    private static URI toUri(String url) throws IOException {
        try {
            return URI.create(url);
        } catch (Exception e) {
            try {
                return new java.net.URL(url).toURI();
            } catch (Exception nested) {
                throw new IOException("Malformed redirect target: " + url);
            }
        }
    }

    private static String resolve(URI base, String location) {
        String lower = location.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return location;
        }
        try {
            return base.resolve(location).toString();
        } catch (Exception e) {
            return location;
        }
    }

    private static void closeQuietly(CloseableHttpClient client) {
        try {
            client.close();
        } catch (Exception ignored) {
        }
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    private static String extractOAuthError(String location) {
        String query = location;
        if (location.contains("#")) {
            query = location.split("#", 2)[1];
        } else if (location.contains("?")) {
            query = location.split("\\?", 2)[1];
        }
        String error = null;
        String description = null;
        for (String param : query.split("&")) {
            if (param.startsWith("error=")) {
                error = param.substring("error=".length());
            } else if (param.startsWith("error_description=")) {
                description = param.substring("error_description=".length());
            }
        }
        if (error == null) {
            return null;
        }
        error = decode(error);
        if (description != null) {
            return error + ": " + decode(description);
        }
        return error;
    }

    private static String extractAccessToken(String location) {
        if (location.contains("#")) {
            String fragment = location.split("#", 2)[1];
            for (String param : fragment.split("&")) {
                if (param.startsWith("access_token=")) {
                    return decode(param.substring("access_token=".length()));
                }
            }
        }
        if (location.contains("access_token=")) {
            int start = location.indexOf("access_token=") + "access_token=".length();
            int end = location.indexOf('&', start);
            return decode(end == -1 ? location.substring(start) : location.substring(start, end));
        }
        return null;
    }
}
