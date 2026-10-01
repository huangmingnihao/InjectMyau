package myau.bot.checks;

import myau.bot.BotCheck;
import myau.util.ServerUtil;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScorePlayerTeam;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SimpleChecks {
    private SimpleChecks() {
    }
    public static final class TabCheck extends BotCheck {
        public TabCheck() {
            super("tab");
        }

        @Override
        public void update() {
            if (mc.getNetHandler() == null) {
                return;
            }
            for (EntityPlayer player : others()) {
                this.set(player, mc.getNetHandler().getPlayerInfo(player.getUniqueID()) == null);
            }
        }
    }
    public static final class NoPingCheck extends BotCheck {
        public NoPingCheck() {
            super("no-ping");
        }
        @Override
        public void update() {
            if (mc.getNetHandler() == null) {
                return;
            }
            for (EntityPlayer player : others()) {
                NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(player.getUniqueID());
                this.set(player, info != null && info.getResponseTime() < 0);
            }
        }
    }

    public static final class NpcUuidCheck extends BotCheck {
        public NpcUuidCheck() {
            super("npc-uuid");
        }
        @Override
        public void update() {
            for (EntityPlayer player : others()) {
                UUID id = player.getUniqueID();
                this.set(player, id == null || id.version() != 4);
            }
        }
    }
    public static final class DuplicateNameCheck extends BotCheck {
        public DuplicateNameCheck() {
            super("duplicate-name");
        }
        @Override
        public void update() {
            Map<String, Integer> counts = new HashMap<String, Integer>();
            for (EntityPlayer player : others()) {
                String name = player.getDisplayName().getUnformattedText();
                Integer seen = counts.get(name);
                counts.put(name, Integer.valueOf(seen == null ? 1 : seen.intValue() + 1));
            }
            for (EntityPlayer player : others()) {
                Integer count = counts.get(player.getDisplayName().getUnformattedText());
                this.set(player, count != null && count.intValue() > 1);
            }
        }
    }

    public static final class DuplicateUuidCheck extends BotCheck {
        public DuplicateUuidCheck() {
            super("duplicate-uuid");
        }
        @Override
        public void update() {
            Map<UUID, Integer> counts = new HashMap<UUID, Integer>();
            for (EntityPlayer player : others()) {
                UUID id = player.getUniqueID();
                if (id == null) {
                    continue;
                }
                Integer seen = counts.get(id);
                counts.put(id, Integer.valueOf(seen == null ? 1 : seen.intValue() + 1));
            }
            for (EntityPlayer player : others()) {
                Integer count = counts.get(player.getUniqueID());
                this.set(player, count != null && count.intValue() > 1);
            }
        }
    }
    public static final class ColourCheck extends BotCheck {
        public ColourCheck() {
            super("colour");
        }
        @Override
        public void update() {
            for (EntityPlayer player : others()) {
                String name = player.getDisplayName().getFormattedText();
                this.set(player, name == null || !name.matches("^§[0-9a-fk-or].*"));
            }
        }
    }

    public static final class FuncraftCheck extends BotCheck {
        public FuncraftCheck() {
            super("funcraft");
        }
        @Override
        public void update() {
            for (EntityPlayer player : others()) {
                this.set(player, !player.getDisplayName().getFormattedText().contains("§"));
            }
        }
    }
    public static final class CubecraftBedrockCheck extends BotCheck {
        public CubecraftBedrockCheck() {
            super("cubecraft-bedrock");
        }
        @Override
        public void update() {
            for (EntityPlayer player : others()) {
                String name = player.getDisplayName().getFormattedText();
                int sections = 0;
                for (int i = 0; i < name.length(); i++) {
                    if (name.charAt(i) == '§') {
                        sections++;
                    }
                }
                this.set(player, sections >= 8 && name.contains("\u00a7\u0000\u00a7\u0000\u00a7\u0000\u00a7\u0000"));
            }
        }
    }
    public static final class TimeVisibleCheck extends BotCheck {
        public TimeVisibleCheck() {
            super("time-visible");
        }
        @Override
        public void update() {
            for (EntityPlayer player : others()) {
                if (player.ticksExisted < 20) {
                    this.mark(player);
                } else {
                    this.unmark(player);
                }
            }
        }
    }
    public static final class HypixelCheck extends BotCheck {
        public HypixelCheck() {
            super("hypixel");
        }
        @Override
        public void update() {
            if (mc.getNetHandler() == null) {
                return;
            }
            for (EntityPlayer player : others()) {
                this.set(player, this.looksLikeBot(player));
            }
        }
        private boolean looksLikeBot(EntityPlayer player) {
            NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(player.getName());
            if (info == null) {
                return true;
            }
            if (!ServerUtil.isHypixel()) {
                return false;
            }
            if (player.isDead || player.getName().isEmpty()) {
                return true;
            }
            if (player.getName().startsWith("§k")) {
                return player.isInvisible();
            }
            if (info.getResponseTime() < 1) {
                return true;
            }
            if (player.getHealth() != 20.0f && player.getName().startsWith("§c")) {
                return true;
            }
            if (player.maxHurtTime == 0) {
                String plain = player.getDisplayName().getUnformattedText();
                if (player.getHealth() == 20.0f) {
                    if (plain.length() == 10 && plain.charAt(0) != '§') {
                        return true;
                    }
                    if (plain.length() == 12 && player.isPlayerSleeping() && plain.charAt(0) == '§') {
                        return true;
                    }
                    if (plain.length() >= 7 && plain.charAt(2) == '[' && plain.charAt(3) == 'N' && plain.charAt(6) == ']') {
                        return true;
                    }
                    if (player.getName().contains(" ")) {
                        return true;
                    }
                } else if (player.isInvisible() && plain.length() >= 3
                        && plain.charAt(0) == '§' && plain.charAt(1) == 'c') {
                    return true;
                }
            }
            ScorePlayerTeam team = info.getPlayerTeam();
            if (team == null || !team.getTeamName().isEmpty()) {
                return false;
            }
            return team.getColorPrefix().equals("§c");
        }
    }
}
