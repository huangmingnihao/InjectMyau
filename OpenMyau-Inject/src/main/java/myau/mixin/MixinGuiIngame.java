package myau.mixin;

import myau.Myau;
import myau.inject.Callbacks;
import myau.module.modules.Scaffold;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(value = {GuiIngame.class}, priority = 9999)
public abstract class MixinGuiIngame {
    @org.spongepowered.asm.mixin.injection.Inject(method = "renderGameOverlay", at = @At("HEAD"))
    private void purgeDebuffs(float partialTicks, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        myau.module.modules.AntiDebuff.purge();
    }

    private static boolean myauHudHookLogged;

    @Inject(
            method = {"renderGameOverlay"},
            at = {@At("HEAD")}
    )
    private void myauRender2DPre(float partialTicks, CallbackInfo callbackInfo) {
        if (!myauHudHookLogged) {
            myauHudHookLogged = true;
            myau.inject.Diag.log("HUD hook reached, partialTicks=" + partialTicks);
        }
        Callbacks.render2DPre(partialTicks);
    }

    @Inject(
            method = {"renderGameOverlay"},
            at = {@At("RETURN")}
    )
    private void myauRender2DPost(float partialTicks, CallbackInfo callbackInfo) {
        Callbacks.render2DPost();
        Callbacks.render2DFrameEnd();
    }

    @Redirect(
            method = {"updateTick"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/InventoryPlayer;getCurrentItem()Lnet/minecraft/item/ItemStack;"
            )
    )
    private ItemStack updateTick(InventoryPlayer inventoryPlayer) {
        Scaffold scaffold = (Scaffold) Myau.moduleManager.modules.get(Scaffold.class);
        if (scaffold.isEnabled() && scaffold.itemSpoof.getValue()) {
            int slot = scaffold.getSlot();
            if (slot >= 0) {
                return inventoryPlayer.getStackInSlot(slot);
            }
        }
        return inventoryPlayer.getCurrentItem();
    }
}
