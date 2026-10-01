package myau.module.modules;

import myau.access.AccessorPlayerControllerMP;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.potion.Potion;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

public class FastBreak extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int MODE_PERCENTAGE = 0;
    private static final int MODE_TICKS = 1;
    public final ModeProperty mode = new ModeProperty("mode", MODE_TICKS, new String[]{"Percentage", "Ticks"});
    public final IntProperty speed = new IntProperty("speed", 50, 0, 100,
            () -> this.mode.getValue() == MODE_PERCENTAGE);
    public final IntProperty ticks = new IntProperty("ticks", 1, 1, 100,
            () -> this.mode.getValue() == MODE_TICKS);
    public final BooleanProperty ignoringMiningFatigue = new BooleanProperty("ignore-mining-fatigue", false);
    public final BooleanProperty equalAirGroundDig = new BooleanProperty("equal-air-ground-dig", true);
    private int offGroundTicks = 0;
    public FastBreak() {
        super("Fast Break", false);
    }
    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }
    @Override
    public void onEnabled() {
        this.offGroundTicks = 0;
    }
    @EventTarget(Priority.MEDIUM)
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }
        if (mc.thePlayer == null || mc.playerController == null || mc.theWorld == null) {
            return;
        }
        if (mc.playerController.isInCreativeMode()) {
            return;
        }
        if (this.ignoringMiningFatigue.getValue()) {
            mc.thePlayer.removePotionEffect(Potion.digSlowdown.getId());
        }
        this.offGroundTicks = mc.thePlayer.onGround ? 0 : this.offGroundTicks + 1;

        AccessorPlayerControllerMP.setBlockHitDelay(mc.playerController, 0);
        float percentageFaster = 0.0F;
        if (this.mode.getValue() == MODE_PERCENTAGE) {
            percentageFaster = this.speed.getValue() / 100.0F;
            if (this.offGroundTicks == 1 && this.equalAirGroundDig.getValue()) {
                AccessorPlayerControllerMP.setCurBlockDamageMP(
                        mc.playerController, AccessorPlayerControllerMP.getCurBlockDamageMP(mc.playerController) / 5.0F);
                percentageFaster = 0.8F;
            }
            if (blockRelativeToPlayer(0.0, mc.thePlayer.motionY, 0.0) != Blocks.air
                    && !mc.thePlayer.onGround
                    && this.equalAirGroundDig.getValue()) {
                AccessorPlayerControllerMP.setCurBlockDamageMP(
                        mc.playerController, AccessorPlayerControllerMP.getCurBlockDamageMP(mc.playerController) * 5.0F);
                percentageFaster -= 0.8F;
            }
        } else {
            if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK) {
                BlockPos blockPos = mc.objectMouseOver.getBlockPos();
                Block block = mc.theWorld.getBlockState(blockPos).getBlock();
                float blockHardness = block.getPlayerRelativeBlockHardness(mc.thePlayer, mc.theWorld, blockPos);
                percentageFaster = blockHardness * this.ticks.getValue();
            }
            if (this.offGroundTicks == 1 && this.equalAirGroundDig.getValue()) {
                AccessorPlayerControllerMP.setCurBlockDamageMP(
                        mc.playerController, AccessorPlayerControllerMP.getCurBlockDamageMP(mc.playerController) / 5.0F);
                percentageFaster = 0.81F;
            }
            if (blockRelativeToPlayer(0.0, mc.thePlayer.motionY, 0.0) != Blocks.air
                    && !mc.thePlayer.onGround
                    && this.equalAirGroundDig.getValue()) {
                AccessorPlayerControllerMP.setCurBlockDamageMP(
                        mc.playerController, AccessorPlayerControllerMP.getCurBlockDamageMP(mc.playerController) * 5.0F);
                percentageFaster -= 0.81F;
            }
        }
        float curBlockDamageMP = AccessorPlayerControllerMP.getCurBlockDamageMP(mc.playerController);
        if (curBlockDamageMP > 1.0F - percentageFaster && curBlockDamageMP < 0.99F) {
            AccessorPlayerControllerMP.setCurBlockDamageMP(mc.playerController, 0.99F);
        }
    }
    private static Block blockRelativeToPlayer(double offsetX, double offsetY, double offsetZ) {
        BlockPos pos = new BlockPos(mc.thePlayer.posX + offsetX, mc.thePlayer.posY + offsetY, mc.thePlayer.posZ + offsetZ);
        return mc.theWorld.getBlockState(pos).getBlock();
    }
}
