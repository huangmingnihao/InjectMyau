package myau.bot.checks;

import myau.bot.BotCheck;
import myau.bot.ProfileLookup;
import net.minecraft.entity.player.EntityPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MojangProfileCheck extends BotCheck {
    private final Set<UUID> pending = new HashSet<UUID>();
    private final Set<UUID> known = new HashSet<UUID>();
    private final Map<String, Integer> firstEntityIdByName = new HashMap<String, Integer>();
    public MojangProfileCheck() {
        super("mojang-profile");
    }
    @Override
    public void update() {
        for (EntityPlayer player : others()) {
            String name = player.getName();
            Integer first = this.firstEntityIdByName.get(name);
            if (first == null) {
                this.firstEntityIdByName.put(name, Integer.valueOf(player.getEntityId()));
            } else if (first.intValue() != player.getEntityId()) {
                this.mark(player);
                continue;
            }
            UUID id = player.getGameProfile() == null ? null : player.getGameProfile().getId();
            if (id == null || id.version() != 4) {
                this.mark(player);
                continue;
            }
            if (this.known.contains(id)) {
                this.mark(player);
                continue;
            }
            this.ask(id);
        }
    }
    private void ask(final UUID id) {
        synchronized (this.pending) {
            if (!this.pending.add(id)) {
                return;
            }
        }
        ProfileLookup.isRealAccount(id, new ProfileLookup.Callback() {
            @Override
            public void accept(boolean real) {
                synchronized (MojangProfileCheck.this.pending) {
                    MojangProfileCheck.this.pending.remove(id);
                }
                if (!real) {
                    synchronized (MojangProfileCheck.this.known) {
                        MojangProfileCheck.this.known.add(id);
                    }
                }
                MojangProfileCheck.this.applyTo(id, !real);
            }
        });
    }
    private void applyTo(UUID id, boolean bot) {
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        try {
            for (EntityPlayer player : others()) {
                if (player.getGameProfile() != null && id.equals(player.getGameProfile().getId())) {
                    this.set(player, bot);
                }
            }
        } catch (Throwable worldChangedUnderUs) {
        }
    }
    @Override
    public void onDisabled() {
        synchronized (this.pending) {
            this.pending.clear();
        }
        synchronized (this.known) {
            this.known.clear();
        }
        this.firstEntityIdByName.clear();
        ProfileLookup.forget();
        super.onDisabled();
    }
}
