package myau.slinky;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import myau.property.Property;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

public class RangeProperty extends Property<RangeProperty.Value> {
   private final double minimum;
   private final double maximum;
   private final double step;

   public RangeProperty(String name, double lower, double upper, double minimum, double maximum, double step) {
      this(name, lower, upper, minimum, maximum, step, null);
   }

   public RangeProperty(String name, double lower, double upper, double minimum, double maximum, double step, BooleanSupplier check) {
      super(name, new Value(lower, upper), validator(minimum, maximum), check);
      this.minimum = minimum;
      this.maximum = maximum;
      this.step = step;
   }

   private static Predicate<Value> validator(double minimum, double maximum) {
      return value -> value.lower >= minimum
            && value.upper <= maximum
            && value.lower <= value.upper
            && Double.isFinite(value.lower)
            && Double.isFinite(value.upper);
   }

   public double getMinimum() {
      return this.minimum;
   }

   public double getMaximum() {
      return this.maximum;
   }

   public double getStep() {
      return this.step;
   }

   public double snap(double value) {
      if (this.step <= 0.0) {
         return value;
      }
      double notches = Math.round((value - this.minimum) / this.step);
      return Math.max(this.minimum, Math.min(this.maximum, this.minimum + notches * this.step));
   }

   @Override
   public String getValuePrompt() {
      return "lower,upper";
   }

   @Override
   public String formatValue() {
      return String.format("%.1f-%.1f", this.getValue().lower, this.getValue().upper);
   }

   @Override
   public boolean parseString(String string) {
      if (string == null) {
         return false;
      }
      String[] parts = string.replace(" ", "").split("[,，\\-]");
      if (parts.length != 2) {
         return false;
      }
      try {
         double lower = Double.parseDouble(parts[0]);
         double upper = Double.parseDouble(parts[1]);
         if (upper < lower) {
            double t = lower;
            lower = upper;
            upper = t;
         }
         return this.setValue(new Value(lower, upper));
      } catch (NumberFormatException e) {
         return false;
      }
   }

   @Override
   public boolean read(JsonObject jsonObject) {
      if (!jsonObject.has(this.getName())) {
         return false;
      }
      JsonArray array = jsonObject.getAsJsonArray(this.getName());
      if (array == null || array.size() < 2) {
         return false;
      }
      return this.setValue(new Value(array.get(0).getAsDouble(), array.get(1).getAsDouble()));
   }

   @Override
   public void write(JsonObject jsonObject) {
      JsonArray array = new JsonArray();
      array.add(new com.google.gson.JsonPrimitive(this.getValue().lower));
      array.add(new com.google.gson.JsonPrimitive(this.getValue().upper));
      jsonObject.add(this.getName(), array);
   }

   public static final class Value {
      public final double lower;
      public final double upper;

      public Value(double lower, double upper) {
         this.lower = lower;
         this.upper = upper;
      }
   }
}
