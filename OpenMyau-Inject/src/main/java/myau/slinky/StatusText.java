package myau.slinky;

import java.util.LinkedHashMap;
import java.util.Map;

public final class StatusText {
   private static final Map<String, String[]> LABELS = new LinkedHashMap<>();

   static {
      LABELS.put("Aim Assist", new String[]{"Lock-on", "Silent"});
      LABELS.put("Hit Select", new String[]{"Burst", "Criticals"});
   }

   private StatusText() {
   }

   public static String label(String name, int index) {
      String[] labels = LABELS.get(name);
      if (labels == null || index < 0 || index >= labels.length) {
         return String.valueOf(index);
      }
      return labels[index];
   }
}
