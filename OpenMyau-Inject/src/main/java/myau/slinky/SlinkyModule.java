package myau.slinky;

import myau.Myau;
import myau.module.Module;
import myau.property.Property;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class SlinkyModule extends Module {
   private Map<String, Property<?>> settingCache;

   protected SlinkyModule(String name) {
      super(name, false);
   }

   protected final boolean flag(String key) {
      return (Boolean) this.setting(key).getValue();
   }

   protected final float number(String key) {
      return ((Number) this.setting(key).getValue()).floatValue();
   }

   protected final float fraction(String key) {
      return this.number(key) / 100.0F;
   }

   protected final int choice(String key) {
      return (Integer) this.setting(key).getValue();
   }

   protected final RangeProperty.Value range(String key) {
      return ((RangeProperty) this.setting(key)).getValue();
   }

   private Property<?> setting(String key) {
      Map<String, Property<?>> cache = this.settingCache;
      if (cache == null) {
         cache = new LinkedHashMap<>();
         if (Myau.propertyManager != null) {
            ArrayList<Property<?>> list = Myau.propertyManager.properties.get(this.getClass());
            if (list != null) {
               for (Property<?> property : list) {
                  cache.put(property.getName(), property);
               }
            }
         }
         this.settingCache = cache;
      }
      Property<?> property = cache.get(key);
      if (property == null) {
         throw new IllegalStateException("Missing recovered setting " + key + " on " + this.getName());
      }
      return property;
   }
}
