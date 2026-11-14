package myau.management;

import myau.enums.DelayModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.network.Packet;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.login.client.C00PacketLoginStart;
import net.minecraft.network.login.client.C01PacketEncryptionResponse;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.S00PacketKeepAlive;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.status.client.C00PacketServerQuery;
import net.minecraft.network.status.client.C01PacketPing;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

public class DelayManager {
    public static Minecraft mc = Minecraft.getMinecraft();
    public DelayModules delayModule = DelayModules.NONE;

    private volatile boolean replaying = false;
    public long delay = 0L;
    public Deque<Packet<INetHandlerPlayClient>> delayedPacket = new ConcurrentLinkedDeque<>();

    public boolean shouldDelay(Packet<INetHandlerPlayClient> packet) {
        if (this.delayModule == DelayModules.NONE) {
            return false;
        } else if (packet instanceof S00PacketKeepAlive) {
            return false;
        } else if (!(packet instanceof S01PacketJoinGame) && !(packet instanceof S07PacketRespawn)) {
            if (packet instanceof S19PacketEntityStatus) {
                S19PacketEntityStatus s19 = (S19PacketEntityStatus) packet;
                Entity entity = s19.getEntity(mc.theWorld);
                if (entity != null && (!entity.equals(mc.thePlayer) || s19.getOpCode() != 2)) {
                    return false;
                }
            }
            this.delayedPacket.offer(packet);
            return true;
        } else {
            this.setDelayState(false, this.delayModule);
            return false;
        }
    }

    public boolean setDelayState(boolean state, DelayModules delayModule) {
        if (state) {
            this.delay = 0;
            this.delayModule = delayModule;
        } else {
            this.delayModule = DelayModules.NONE;
            if (Minecraft.getMinecraft().getNetHandler() != null && this.delayedPacket.isEmpty()) {
                return true;
            }
            this.replaying = true;
            try {
                while (true) {
                    Packet<INetHandlerPlayClient> packet = this.delayedPacket.poll();
                    if (packet == null) {
                        this.delayedPacket.clear();
                        break;
                    }
                    packet.processPacket(Minecraft.getMinecraft().getNetHandler());
                }
            } finally {
                this.replaying = false;
            }
        }
        return this.delayModule != DelayModules.NONE;
    }


    public boolean isReplaying() {
        return this.replaying;
    }

    public DelayModules getDelayModule() {
        return this.delayModule;
    }

    public synchronized boolean beginDelay(
            DelayModules owner, Packet<INetHandlerPlayClient> firstPacket) {
        if (owner == DelayModules.NONE
                || this.delayModule != DelayModules.NONE
                || !this.delayedPacket.isEmpty()) {
            return false;
        }
        this.delayedPacket.offer(firstPacket);
        this.delay = 0L;
        this.delayModule = owner;
        return true;
    }

    public synchronized void discardDelay(DelayModules owner) {
        if (this.delayModule != owner) {
            return;
        }
        this.delayModule = DelayModules.NONE;
        this.delayedPacket.clear();
        this.delay = 0L;
    }

    public synchronized void releaseDelay(DelayModules owner) {
        if (this.delayModule != owner) {
            return;
        }
        if (Minecraft.getMinecraft().getNetHandler() == null) {
            this.discardDelay(owner);
        } else {
            this.setDelayState(false, owner);
        }
    }

    public void delay(DelayModules modules) {
        this.delayModule = modules;
    }

    public long getDelay() {
        return this.delay;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getPacket() instanceof C00Handshake
                || event.getPacket() instanceof C00PacketLoginStart
                || event.getPacket() instanceof C00PacketServerQuery
                || event.getPacket() instanceof C01PacketPing
                || event.getPacket() instanceof C01PacketEncryptionResponse) {
            this.setDelayState(false, this.delayModule);
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() == EventType.POST) {
            if (mc.thePlayer.isDead) {
                this.setDelayState(false, this.delayModule);
            }
            if (this.delayModule != DelayModules.NONE) {
                this.delay++;
            }
        }
    }
}
