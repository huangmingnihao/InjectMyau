package myau.ui.clickgui;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

public enum Category {
    COMBAT("Combat"),
    MOVEMENT("Movement"),
    RENDER("Render"),
    PLAYER("Player"),
    MISC("Misc"),
    THEME("Settings");
    private final String label;
    private ItemStack icon;
    private ItemStack activeIcon;
    private boolean iconsBuilt;
    Category(String label) {
        this.label = label;
    }
    public String getLabel() {
        return this.label;
    }
    public ItemStack getIcon(boolean active) {
        if (!this.iconsBuilt) {
            this.iconsBuilt = true;
            try {
                this.icon = this.makeIcon(false);
                this.activeIcon = this.makeIcon(true);
            } catch (Throwable notReady) {
                this.icon = null;
                this.activeIcon = null;
            }
        }
        return active ? this.activeIcon : this.icon;
    }
    private ItemStack makeIcon(boolean active) {
        ItemStack stack;
        switch (this) {
            case COMBAT:
                stack = new ItemStack(Items.diamond_sword);
                break;
            case MOVEMENT:
                stack = new ItemStack(Items.diamond_boots);
                break;
            case RENDER:
                stack = new ItemStack(Items.ender_eye);
                break;
            case PLAYER:
                stack = new ItemStack(Items.golden_apple);
                break;
            case THEME:
                stack = new ItemStack(Items.dye, 1, 4);
                break;
            default:
                stack = new ItemStack(Items.clock);
                break;
        }
        if (!active) {
            return stack;
        }
        if (this == PLAYER) {
            stack.setItemDamage(1);
        } else {
            stack.addEnchantment(Enchantment.unbreaking, 2);
        }
        return stack;
    }
}
