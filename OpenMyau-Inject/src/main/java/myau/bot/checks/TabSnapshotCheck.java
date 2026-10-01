package myau.bot.checks;

import myau.Myau;
import myau.bot.BotCheck;
import myau.module.modules.KillAura;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class TabSnapshotCheck extends BotCheck {
    private final Set<UUID> roster = new HashSet<UUID>();
    private boolean captured;
    public TabSnapshotCheck() {
        super("tab-snapshot");
    }
    @Override
    public void update() {
        if (mc.getNetHandler() == null) {
            return;
        }
        if (!this.captured) {
            this.captureIfFightStarted();
        }
        if (!this.captured) {

            this.clear();
            return;
        }
        for (EntityPlayer player : others()) {
            this.set(player, !this.roster.contains(player.getUniqueID()));
        }
    }
    private void captureIfFightStarted() {
        if (mc.thePlayer.ticksExisted < 150) {
            return;
        }
        KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        if (killAura == null || !killAura.isEnabled()) {
            return;
        }
        EntityLivingBase target = killAura.getTarget();
        if (target == null || target.isDead) {
            return;
        }
        this.roster.clear();
        for (NetworkPlayerInfo info : mc.getNetHandler().getPlayerInfoMap()) {
            if (info != null && info.getGameProfile() != null) {
                this.roster.add(info.getGameProfile().getId());
            }
        }
        this.captured = true;
    }
    @Override
    public void onDisabled() {
        this.captured = false;
        this.roster.clear();
        super.onDisabled();
    }
}
