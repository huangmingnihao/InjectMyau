package myau.access;

import myau.inject.MappingBridge;
import net.minecraft.entity.projectile.EntityArrow;
import java.lang.reflect.Field;

public final class AccessorEntityArrow {
    private static final String OWNER = "net.minecraft.entity.projectile.EntityArrow";
    private static final Field IN_GROUND = MappingBridge.field(OWNER, "inGround", null);
    private AccessorEntityArrow() { }

    public static boolean isInGround(EntityArrow arrow) {
        if (IN_GROUND == null) return arrow.onGround || arrow.motionX * arrow.motionX
                + arrow.motionY * arrow.motionY + arrow.motionZ * arrow.motionZ < 1.0E-10;
        try {
            return IN_GROUND.getBoolean(arrow);
        } catch (IllegalAccessException e) {
            Access.report(OWNER, "inGround", e);
            return arrow.onGround;
        }
    }
}
