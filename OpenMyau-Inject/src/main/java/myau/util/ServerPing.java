package myau.util;

import myau.event.EventTarget;
import myau.events.PlayerUpdateEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerAddress;
import net.minecraft.client.multiplayer.ServerData;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ServerPing {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final long DEFAULT_MS = 250L;
    private static final long REFRESH_MS = 10000L;
    private static final long IDLE_MS = 120000L;
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "myau-ping");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile long ping = DEFAULT_MS;
    private static final TimerUtil sinceMeasure = new TimerUtil();
    private static final TimerUtil sinceRequest = new TimerUtil();
    public ServerPing() {
    }
    public static long getPing() {
        if (sinceRequest.hasTimeElapsed(IDLE_MS)) {
            measure();
            sinceRequest.reset();
            return DEFAULT_MS;
        }
        sinceRequest.reset();
        return ping;
    }
    @EventTarget
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (sinceMeasure.hasTimeElapsed(REFRESH_MS) && !sinceRequest.hasTimeElapsed(IDLE_MS)) {
            measure();
        }
    }
    private static void measure() {
        sinceMeasure.reset();

        if (mc.isIntegratedServerRunning()) {
            ping = 0L;
            return;
        }
        ServerData data = mc.getCurrentServerData();
        if (data == null || data.serverIP == null || data.serverIP.isEmpty()) {
            return;
        }
        String address = data.serverIP;
        WORKER.execute(() -> {
            long measured = measureOnce(address);
            if (measured > 0L) {
                ping = measured;
            }
        });
    }

    private static long measureOnce(String address) {
        try {
            ServerAddress parsed = ServerAddress.fromString(address);
            try (Socket socket = new Socket()) {
                long start = System.currentTimeMillis();
                socket.connect(new InetSocketAddress(parsed.getIP(), parsed.getPort()), CONNECT_TIMEOUT_MS);
                return System.currentTimeMillis() - start;
            }
        } catch (Throwable ignored) {
            return 0L;
        }
    }
}
