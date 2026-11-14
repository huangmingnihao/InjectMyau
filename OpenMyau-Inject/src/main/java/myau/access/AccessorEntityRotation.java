package myau.access;

import java.lang.reflect.Method;

import myau.inject.MappingBridge;
import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;













public final class AccessorEntityRotation {

    private static final String OWNER = "net.minecraft.entity.Entity";
    private static final Method M_GET_VECTOR_FOR_ROTATION =
            MappingBridge.method(OWNER, "getVectorForRotation", float.class, float.class);

    private AccessorEntityRotation() {
    }




    public static Vec3 getVectorForRotation(Entity entity, float pitch, float yaw) {
        if (entity == null || M_GET_VECTOR_FOR_ROTATION == null) {
            return null;
        }
        try {
            return (Vec3) M_GET_VECTOR_FOR_ROTATION.invoke(entity, pitch, yaw);
        } catch (Throwable swallowed) {
            return null;
        }
    }
}
