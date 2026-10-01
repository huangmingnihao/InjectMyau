package myau.module.modules;

import com.mojang.authlib.GameProfile;
import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScorePlayerTeam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 *
 *
 */
public class AntiBot extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanProperty delay = new BooleanProperty("delay", false);
    public final FloatProperty delaySeconds = new FloatProperty("delay-seconds", 0.5F, 0.5F, 15.0F, 0.5F,
            () -> this.delay.getValue());
    public final BooleanProperty tablist = new BooleanProperty("tablist-check", false);

    private static final Map<Integer, Long> joinTime = new ConcurrentHashMap<Integer, Long>();
    private static volatile List<String> tabNames = Collections.emptyList();

    public AntiBot() {
        super("Anti Bot", true);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE || !this.isEnabled()) {
            return;
        }
        if (mc.getNetHandler() == null) {
            tabNames = Collections.emptyList();
            joinTime.clear();
            return;
        }
        if (tablist.getValue()) {
            List<String> names = new ArrayList<String>();
            for (NetworkPlayerInfo info : mc.getNetHandler().getPlayerInfoMap()) {
                GameProfile profile = info.getGameProfile();
                if (profile != null && mc.thePlayer != null
                        && !profile.getId().equals(mc.thePlayer.getUniqueID())) {
                    names.add(profile.getName());
                }
            }
            tabNames = Collections.unmodifiableList(names);
        } else {
            tabNames = Collections.emptyList();
        }
        if (delay.getValue() && mc.theWorld != null) {
            long now = System.currentTimeMillis();
            for (Object o : mc.theWorld.playerEntities) {
                EntityPlayer p = (EntityPlayer) o;
                if (p != mc.thePlayer && !joinTime.containsKey(p.getEntityId())) {
                    joinTime.put(Integer.valueOf(p.getEntityId()), Long.valueOf(now));
                }
            }
            long windowMs = (long) (delaySeconds.getValue().floatValue() * 1000.0F);
            joinTime.values().removeIf(t -> t.longValue() < now - windowMs);
        } else if (!joinTime.isEmpty()) {
            joinTime.clear();
        }
    }

    public static boolean isBot(Entity entity) {
        AntiBot module = (AntiBot) Myau.moduleManager.modules.get(AntiBot.class);
        if (module == null || !module.isEnabled()) {
            return false;
        }
        if (mc.isSingleplayer()) {
            return false;
        }
        if (entity == null || !(entity instanceof EntityPlayer)) {
            return false;
        }
        EntityPlayer p = (EntityPlayer) entity;
        if (p == mc.thePlayer) {
            return false;
        }
        if (module.delay.getValue() && joinTime.containsKey(Integer.valueOf(p.getEntityId()))) {
            return true;
        }
        if (p.isDead) {
            return true;
        }
        if (p.getName().isEmpty()) {
            return true;
        }
        if (module.tablist.getValue() && !tabNames.contains(p.getName())) {
            return true;
        }
        if (p.getHealth() != 20.0F && p.getName().startsWith("§c")) {
            return true;
        }
        if (p.maxHurtTime == 0) {
            String unformatted = canonicalName(p);
            if (p.getHealth() == 20.0F) {
                if (unformatted.length() == 10 && unformatted.charAt(0) != '§') {
                    return true;
                }
                if (unformatted.length() == 12 && p.isPlayerSleeping() && unformatted.charAt(0) == '§') {
                    return true;
                }
                if (unformatted.length() >= 7 && unformatted.charAt(2) == '['
                        && unformatted.charAt(3) == 'N' && unformatted.charAt(6) == ']') {
                    return true;
                }
                if (p.getName().contains(" ")) {
                    return true;
                }
            } else if (p.isInvisible()) {
                if (unformatted.length() >= 3 && unformatted.charAt(0) == '§'
                        && unformatted.charAt(1) == 'c') {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     */
    private static String canonicalName(EntityPlayer p) {
        ScorePlayerTeam team = (ScorePlayerTeam) p.getTeam();
        if (team == null) {
            return p.getName();
        }
        String prefix = team.getColorPrefix();
        String suffix = team.getColorSuffix();
        return (prefix == null ? "" : prefix) + p.getName() + (suffix == null ? "" : suffix);
    }
}
