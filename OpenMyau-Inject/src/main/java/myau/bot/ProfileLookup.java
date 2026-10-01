package myau.bot;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public final class ProfileLookup {
    private static final String SESSION_SERVER =
            "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final long MINIMUM_GAP_MS = 100L;
    private static final long CACHE_LIFETIME_MS = 5L * 60L * 1000L;
    private static final Map<UUID, Answer> ANSWERS = new ConcurrentHashMap<UUID, Answer>();
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "myau-profile-lookup");
            thread.setDaemon(true);
            return thread;
        }
    });
    private static long lastRequestAt;
    private ProfileLookup() {
    }
    public interface Callback {
        void accept(boolean real);
    }
    public static void isRealAccount(UUID id, Callback callback) {
        if (id == null) {
            callback.accept(false);
            return;
        }
        Answer cached = ANSWERS.get(id);
        if (cached != null && !cached.isStale()) {
            callback.accept(cached.real);
            return;
        }
        final UUID wanted = id;
        final Callback answerTo = callback;
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                boolean real = fetch(wanted);
                ANSWERS.put(wanted, new Answer(real));
                answerTo.accept(real);
            }
        });
    }
    public static void forget() {
        ANSWERS.clear();
    }
    private static boolean fetch(UUID id) {
        HttpURLConnection connection = null;
        try {
            long since = System.currentTimeMillis() - lastRequestAt;
            if (since < MINIMUM_GAP_MS) {
                Thread.sleep(MINIMUM_GAP_MS - since);
            }
            lastRequestAt = System.currentTimeMillis();
            String plain = id.toString().replace("-", "");
            connection = (HttpURLConnection) new URL(SESSION_SERVER + plain).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "Myau-checkbot");
            int status = connection.getResponseCode();
            if (status == 204 || status == 404) {
                return false;
            }
            if (status != 200) {
                return true;
            }
            StringBuilder body = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
            reader.close();
            JsonObject json = new JsonParser().parse(body.toString()).getAsJsonObject();
            return json.has("id")
                    && json.has("name")
                    && json.get("id").getAsString().replace("-", "").equalsIgnoreCase(plain);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return true;
        } catch (Throwable failed) {
            return true;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    private static final class Answer {
        final boolean real;
        private final long at;
        Answer(boolean real) {
            this.real = real;
            this.at = System.currentTimeMillis();
        }
        boolean isStale() {
            return System.currentTimeMillis() - this.at > CACHE_LIFETIME_MS;
        }
    }
}
