package myau.slinky.silent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;

/** Vanilla-only adapter for the reference client's context. */
public final class SilentAimContext {
    private final Minecraft minecraft;
    public SilentAimContext(Minecraft minecraft) { this.minecraft = minecraft; }
    public Minecraft minecraft() { return minecraft; }
    public EntityPlayerSP player() { return minecraft == null ? null : minecraft.thePlayer; }
    public WorldClient world() { return minecraft == null ? null : minecraft.theWorld; }
    public boolean ready() { return player() != null && world() != null; }
    public boolean inputReady() { return ready() && minecraft.inGameHasFocus && minecraft.currentScreen == null; }
}
