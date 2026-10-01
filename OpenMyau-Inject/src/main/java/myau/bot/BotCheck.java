package myau.bot;

import myau.Myau;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

import java.util.ArrayList;
import java.util.List;

public abstract class BotCheck {
    protected static final Minecraft mc = Minecraft.getMinecraft();
    private final String name;
    protected BotCheck(String name) {
        this.name = name;
    }
    public String getName() {
        return this.name;
    }

    public abstract void update();
    public void onDisabled() {
        this.clear();
    }
    protected void mark(Entity entity) {
        Myau.botManager.mark(this, entity);
    }
    protected void unmark(Entity entity) {
        Myau.botManager.unmark(this, entity);
    }
    protected void set(Entity entity, boolean bot) {
        if (bot) {
            this.mark(entity);
        } else {
            this.unmark(entity);
        }
    }
    protected boolean isMarked(Entity entity) {
        return Myau.botManager.isBot(this, entity);
    }
    protected void clear() {
        Myau.botManager.clear(this);
    }
    protected static List<EntityPlayer> others() {
        List<EntityPlayer> players = new ArrayList<EntityPlayer>();
        for (Object raw : mc.theWorld.playerEntities) {
            if (raw instanceof EntityPlayer && raw != mc.thePlayer) {
                players.add((EntityPlayer) raw);
            }
        }
        return players;
    }
}
