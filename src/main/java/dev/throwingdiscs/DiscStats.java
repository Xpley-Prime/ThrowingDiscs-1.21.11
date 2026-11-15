package dev.throwingdiscs;

/**
 * Approximate, configurable-by-code values: Mojang has not published the
 * trailer's final exact weapon statistics.
 */
public record DiscStats(String name, int durability, float damage, double speed,
                        double maxRange, int flightTicks, int maxBounces,
                        int maxTargets, int cooldownTicks) { }
