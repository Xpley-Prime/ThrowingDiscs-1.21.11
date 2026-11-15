package dev.throwingdiscs;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Uses a normal item entity for the disc visual; its flight is simulated on the
 * server. No custom entity renderer or client-only dependency is required.
 * Position is synchronized to every nearby player by vanilla entity networking.
 */
public final class DiscFlights {
    private DiscFlights() {}

    private static final Map<UUID, Flight> ACTIVE = new HashMap<>();

    public static boolean launch(ServerLevel level, ServerPlayer owner, ItemStack stack,
                                 DiscStats stats, boolean returnsToInventory) {
        Vec3 heading = owner.getLookAngle().normalize();
        Vec3 start = owner.getEyePosition().add(heading.scale(0.60));
        ItemEntity entity = new ItemEntity(level, start.x, start.y, start.z, stack.copy());
        entity.noPhysics = true;   // we calculate our own block collision / ricochet
        entity.setNoGravity(true);
        entity.setInvulnerable(true);
        entity.setPickUpDelay(300);  // recoverable after a crash/restart
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setThrower(owner);
        if (!level.addFreshEntity(entity)) return false;
        ACTIVE.put(entity.getUUID(), new Flight(level, owner.getUUID(), entity,
                stats, heading, returnsToInventory));
        return true;
    }

    public static void tick(MinecraftServer server) {
        Iterator<Flight> iterator = ACTIVE.values().iterator();
        while (iterator.hasNext()) {
            Flight flight = iterator.next();
            if (flight.entity.isRemoved()) {
                iterator.remove();
                continue;
            }
            if (step(server, flight)) iterator.remove();
        }
    }

    /** Called on shutdown so floating discs do not become uncollectable. */
    public static void releaseAll() {
        for (Flight f : ACTIVE.values()) release(f);
        ACTIVE.clear();
    }

    /** @return true when the flight is complete. */
    private static boolean step(MinecraftServer server, Flight f) {
        ServerPlayer owner = server.getPlayerList().getPlayer(f.ownerId);
        if (owner == null || owner.isRemoved() || owner.isDeadOrDying() || owner.level() != f.level) {
            release(f);
            return true;
        }
        if (++f.ticks >= 160) {
            f.returning = true;
        }

        Vec3 from = f.entity.position();
        if (f.returning) {
            Vec3 destination = owner.getEyePosition().add(0, -0.25, 0);
            Vec3 toOwner = destination.subtract(from);
            if (toOwner.lengthSqr() < 1.7) {
                finish(f, owner);
                return true;
            }
            double speed = f.stats.speed() * 1.25;
            move(f.entity, from.add(toOwner.normalize().scale(Math.min(speed, toOwner.length()))));
            return false;
        }

        Vec3 end = from.add(f.heading.scale(f.stats.speed()));
        if (!f.level.hasChunkAt(BlockPos.containing(end))) {
            f.returning = true;
            return false;
        }

        // First detect the nearest solid block, then sweep for targets only up to that block.
        BlockHitResult block = f.level.clip(new ClipContext(from, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, f.entity));
        boolean hitBlock = block.getType() == HitResult.Type.BLOCK;
        Vec3 cappedEnd = hitBlock ? block.getLocation() : end;

        List<LivingEntity> candidates = f.level.getEntitiesOfClass(LivingEntity.class,
                new AABB(from, cappedEnd).inflate(0.65), target ->
                        target.isAlive() && !target.getUUID().equals(f.ownerId)
                        && !f.hitIds.contains(target.getUUID()) && !target.isSpectator());
        // Segment-vs-expanded-hitbox test avoids hitting entities behind the disc.
        List<LivingEntity> hit = new ArrayList<>();
        for (LivingEntity candidate : candidates) {
            if (candidate.getBoundingBox().inflate(0.32).clip(from, cappedEnd).isPresent())
                hit.add(candidate);
        }
        hit.sort(Comparator.comparingDouble(e -> e.position().distanceToSqr(from)));

        for (LivingEntity target : hit) {
            f.hitIds.add(target.getUUID());
            if (target.hurtServer(f.level, f.level.damageSources().playerAttack(owner), f.stats.damage())) {
                // Subtle knockback in the travel direction (vanilla armor still applies).
                target.push(f.heading.x * 0.22, 0.08, f.heading.z * 0.22);
            }
            f.targetsHit++;
            if (f.targetsHit >= f.stats.maxTargets()) {
                f.returning = true;
                break;
            }
        }

        f.traveled += from.distanceTo(cappedEnd);
        if (f.traveled >= f.stats.range()) f.returning = true;
        if (f.returning) {
            move(f.entity, cappedEnd);
            return false;
        }

        if (hitBlock) {
            move(f.entity, cappedEnd);
            if (f.bounces >= f.stats.maxBounces()) {
                f.returning = true;
            } else {
                f.bounces++;
                Direction face = block.getDirection();
                Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
                f.heading = reflect(f.heading, normal).normalize();
                move(f.entity, cappedEnd.add(f.heading.scale(0.12)));
                f.level.playSound(null, cappedEnd.x, cappedEnd.y, cappedEnd.z,
                        SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.18f, 1.75f);
            }
        } else {
            move(f.entity, end);
        }
        return false;
    }

    /** Unit test friendly: mirror the incoming vector across a plane normal. */
    static Vec3 reflect(Vec3 incoming, Vec3 normal) {
        return incoming.subtract(normal.scale(2.0 * incoming.dot(normal)));
    }

    private static void move(ItemEntity entity, Vec3 to) {
        entity.setPos(to.x, to.y, to.z);
        entity.setDeltaMovement(Vec3.ZERO);
    }

    private static void finish(Flight f, ServerPlayer owner) {
        ItemStack toGive = f.entity.getItem().copy();
        f.entity.discard();
        if (f.returnToInventory && !toGive.isEmpty()) {
            if (!owner.addItem(toGive) && !toGive.isEmpty()) owner.drop(toGive, false);
        } else if (!f.returnToInventory && !owner.getAbilities().instabuild) {
            f.level.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                    SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.7f, 1.2f);
        }
    }

    private static void release(Flight f) {
        if (f.entity.isRemoved()) return;
        f.entity.noPhysics = false;
        f.entity.setInvulnerable(false);
        f.entity.setNoGravity(false);
        f.entity.setDeltaMovement(0, 0.1, 0);
        f.entity.setPickUpDelay(10);
        // If the throw was creative or the disc broke, don't leave free extra items.
        if (!f.returnToInventory) f.entity.discard();
    }

    private static final class Flight {
        final ServerLevel level;
        final UUID ownerId;
        final ItemEntity entity;
        final DiscStats stats;
        final Set<UUID> hitIds = new HashSet<>();
        final boolean returnToInventory;
        Vec3 heading;
        double traveled;
        int ticks;
        int bounces;
        int targetsHit;
        boolean returning;

        Flight(ServerLevel level, UUID ownerId, ItemEntity entity,
               DiscStats stats, Vec3 heading, boolean returnToInventory) {
            this.level = level;
            this.ownerId = ownerId;
            this.entity = entity;
            this.stats = stats;
            this.heading = heading;
            this.returnToInventory = returnToInventory;
        }
    }
}
