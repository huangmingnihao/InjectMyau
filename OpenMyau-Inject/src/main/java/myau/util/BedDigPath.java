package myau.util;

import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/** Conservative block-cell tracing and complete-route scoring for bed defenses. */
public final class BedDigPath {
    private BedDigPath() {
    }

    public static List<BlockPos> trace(Vec3 start, Vec3 end) {
        List<BlockPos> cells = new ArrayList<>();
        int x = MathHelper.floor_double(start.xCoord);
        int y = MathHelper.floor_double(start.yCoord);
        int z = MathHelper.floor_double(start.zCoord);
        BlockPos goal = new BlockPos(end);
        double dx = end.xCoord - start.xCoord;
        double dy = end.yCoord - start.yCoord;
        double dz = end.zCoord - start.zCoord;
        int sx = dx > 0.0 ? 1 : dx < 0.0 ? -1 : 0;
        int sy = dy > 0.0 ? 1 : dy < 0.0 ? -1 : 0;
        int sz = dz > 0.0 ? 1 : dz < 0.0 ? -1 : 0;
        double tx = sx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
        double ty = sy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
        double tz = sz == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);
        double nx = sx == 0 ? Double.POSITIVE_INFINITY : (sx > 0 ? x + 1.0 - start.xCoord : start.xCoord - x) * tx;
        double ny = sy == 0 ? Double.POSITIVE_INFINITY : (sy > 0 ? y + 1.0 - start.yCoord : start.yCoord - y) * ty;
        double nz = sz == 0 ? Double.POSITIVE_INFINITY : (sz > 0 ? z + 1.0 - start.zCoord : start.zCoord - z) * tz;
        int steps = Math.abs(goal.getX() - x) + Math.abs(goal.getY() - y) + Math.abs(goal.getZ() - z) + 1;
        for (int i = 0; i < steps; i++) {
            BlockPos cell = new BlockPos(x, y, z);
            cells.add(cell);
            if (cell.equals(goal)) {
                break;
            }
            double next = Math.min(nx, Math.min(ny, nz));
            if (next > 1.0) {
                break;
            }
            // Advance tied axes together: cells merely touching a ray corner are not crossed.
            if (nx <= next + 1.0E-10) {
                x += sx;
                nx += tx;
            }
            if (ny <= next + 1.0E-10) {
                y += sy;
                ny += ty;
            }
            if (nz <= next + 1.0E-10) {
                z += sz;
                nz += tz;
            }
        }
        return cells;
    }

    public static Route evaluate(List<BlockPos> cells, Predicate<BlockPos> passable,
                                 Predicate<BlockPos> diggable, Predicate<BlockPos> goal,
                                 ToDoubleFunction<BlockPos> ticks, ToDoubleFunction<BlockPos> distance) {
        BlockPos first = null;
        int count = 0;
        double totalTicks = 0.0;
        for (int i = 0; i < cells.size(); i++) {
            BlockPos cell = cells.get(i);
            if (passable.test(cell)) {
                continue;
            }
            if (!diggable.test(cell)) {
                return null;
            }
            if (first == null) {
                first = cell;
            }
            count++;
            totalTicks += ticks.applyAsDouble(cell);
            if (goal.test(cell)) {
                return new Route(first, count, totalTicks, distance.applyAsDouble(first));
            }
        }
        return null;
    }

    public static final class Route implements Comparable<Route> {
        public final BlockPos target;
        public final int blocks;
        public final double ticks;
        public final double distance;

        public Route(BlockPos target, int blocks, double ticks, double distance) {
            this.target = target;
            this.blocks = blocks;
            this.ticks = ticks;
            this.distance = distance;
        }

        @Override
        public int compareTo(Route other) {
            int order = Integer.compare(this.blocks, other.blocks);
            if (order == 0) {
                order = Double.compare(this.ticks, other.ticks);
            }
            return order == 0 ? Double.compare(this.distance, other.distance) : order;
        }
    }
}
