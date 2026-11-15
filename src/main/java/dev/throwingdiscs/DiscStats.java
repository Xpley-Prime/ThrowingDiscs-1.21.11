package dev.throwingdiscs;

/** The five variants share one codepath; only these balancing parameters differ. */
public record DiscStats(
        String name, int durability, float damage, double speed,
        double range, int maxBounces, int maxTargets, int cooldownTicks
) {
}
