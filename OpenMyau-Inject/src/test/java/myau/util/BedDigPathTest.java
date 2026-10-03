package myau.util;

import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Dependency-free regression runner; invoked by the Gradle check task. */
public final class BedDigPathTest {
    public static void main(String[] args) {
        Vec3 eyes = new Vec3(0.5, 0.5, 0.5);
        BlockPos bed = new BlockPos(4, 0, 0);
        List<BlockPos> straight = BedDigPath.trace(eyes, new Vec3(4.5, 0.5, 0.5));
        check(straight.equals(Arrays.asList(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0),
                new BlockPos(2, 0, 0), new BlockPos(3, 0, 0), bed)), "axis-aligned ray");
        check(BedDigPath.trace(new Vec3(4.5, 0.5, 0.5), eyes).size() == 5, "reverse ray");
        check(BedDigPath.trace(eyes, eyes).size() == 1, "zero-length ray");
        check(BedDigPath.trace(new Vec3(-0.5, -0.5, -0.5), new Vec3(-3.5, -3.5, -3.5))
                .equals(Arrays.asList(new BlockPos(-1, -1, -1), new BlockPos(-2, -2, -2),
                        new BlockPos(-3, -3, -3), new BlockPos(-4, -4, -4))), "negative coordinates and tied axes");

        Set<BlockPos> solids = new HashSet<>(Arrays.asList(new BlockPos(1, 0, 0), new BlockPos(2, 0, 0), bed));
        BedDigPath.Route longRoute = evaluate(straight, solids, bed);
        solids.remove(new BlockPos(1, 0, 0));
        BedDigPath.Route shortRoute = evaluate(straight, solids, bed);
        check(longRoute.blocks == 3 && shortRoute.blocks == 2, "counts the entire defense route");
        check(shortRoute.compareTo(longRoute) < 0, "fewer blocks wins despite a farther first target");
        check(shortRoute.target.equals(new BlockPos(2, 0, 0)), "starts with first remaining blocker");

        // A ray above a lower slab still crosses its occupied block cell.
        List<BlockPos> aboveSlab = BedDigPath.trace(new Vec3(0.5, 0.9, 0.5), new Vec3(4.5, 0.9, 0.5));
        Set<BlockPos> defense = new HashSet<>(Arrays.asList(new BlockPos(3, 0, 0), bed));
        BedDigPath.Route covered = evaluate(aboveSlab, defense, bed);
        check(covered.blocks == 2 && covered.target.equals(new BlockPos(3, 0, 0)), "partial block must be mined before bed");
        defense.add(new BlockPos(0, 0, 0));
        check(evaluate(aboveSlab, defense, bed).target.equals(new BlockPos(0, 0, 0)), "partial block in eye cell also blocks");
        BedDigPath.Route exposed = evaluate(straight, new HashSet<>(Arrays.asList(bed)), bed);
        check(exposed.blocks == 1 && exposed.target.equals(bed), "exposed bed can be mined directly");
        check(BedDigPath.evaluate(straight, pos -> !solids.contains(pos), pos -> !pos.equals(new BlockPos(2, 0, 0)),
                bed::equals, pos -> 1.0, pos -> 1.0) == null, "unbreakable or missing-tool blocker rejects route");
        check(BedDigPath.evaluate(straight, pos -> !solids.contains(pos), pos -> false,
                bed::equals, pos -> 1.0, pos -> 1.0) == null, "surroundings disabled rejects covered bed");
        check(BedDigPath.evaluate(straight, pos -> !solids.contains(pos), pos -> true,
                new BlockPos(5, 0, 0)::equals, pos -> 1.0, pos -> 1.0) == null, "route must reach its bed");
        check(new BedDigPath.Route(bed, 2, 10, 9).compareTo(new BedDigPath.Route(bed, 2, 20, 1)) < 0,
                "equal block counts prefer less mining time");
        check(new BedDigPath.Route(bed, 2, 10, 1).compareTo(new BedDigPath.Route(bed, 2, 10, 9)) < 0,
                "equal mining time prefers closer target");
        System.out.println("BedDigPath regression checks passed");
    }

    private static BedDigPath.Route evaluate(List<BlockPos> cells, Set<BlockPos> solids, BlockPos bed) {
        return BedDigPath.evaluate(cells, pos -> !solids.contains(pos), pos -> true, bed::equals,
                pos -> 5.0, pos -> pos.distanceSqToCenter(0.5, 0.5, 0.5));
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
