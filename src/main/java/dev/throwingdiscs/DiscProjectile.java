package dev.throwingdiscs;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Real, saveable projectile with its own hitbox, server-side sweep collision and
 * persistent item stack. A vanilla item_display is its thin rotating 3D visual;
 * unlike the 1.0 prototype it is NOT a thrown ItemEntity.
 */
public final class DiscProjectile extends ThrowableItemProjectile {
    private static final int MAX_LIFETIME = 240;
    private static final double ENTITY_RADIUS = .32;

    private Vec3 flight = new Vec3(0, 0, 1);
    private double travelled;
    private int outwardTicks;
    private int bounces;
    private int pierced;
    private boolean returning;
    private boolean offHand;
    private boolean creative;
    private boolean delivered;
    private UUID throwerId;
    private UUID displayId;
    private final Set<UUID> alreadyHit = new HashSet<>();

    public DiscProjectile(EntityType<? extends ThrowableItemProjectile> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public DiscProjectile(ServerLevel level, ServerPlayer owner, ItemStack stack,
                          InteractionHand hand, boolean creative) {
        super(ThrowingDiscs.PROJECTILE, owner, level, stack);
        this.throwerId = owner.getUUID();
        this.offHand = hand == InteractionHand.OFF_HAND;
        this.creative = creative;
        this.flight = owner.getLookAngle().normalize();
        Vec3 origin = owner.getEyePosition().add(this.flight.scale(.75))
                .add(0, -.13, 0);
        setPos(origin.x, origin.y, origin.z);
        setNoGravity(true);
        setItem(stack.copy());
        setOwner(owner);
        setDeltaMovement(Vec3.ZERO);
        setRequiresPrecisePosition(true);
    }

    @Override
    protected Item getDefaultItem() {
        return ThrowingDiscs.IRON;
    }

    @Override
    protected double getDefaultGravity() {
        return 0;
    }

    private DiscStats stats() {
        Item item = getItem().getItem();
        return item instanceof DiscItem disc ? disc.stats() : ThrowingDiscs.IRON.stats();
    }

    @Override
    public void tick() {
        // Vanilla projectile's own velocity stays zero. It maintains entity
        // bookkeeping; this mod integrates the disc's path exactly once per tick.
        setDeltaMovement(Vec3.ZERO);
        super.tick();
        if (!(level() instanceof ServerLevel world) || isRemoved()) return;

        if (delivered) {
            discard();
            return;
        }

        ServerPlayer owner = throwerId == null ? null :
                world.getServer().getPlayerList().getPlayer(throwerId);

        if (owner == null || owner.level() != world || owner.isDeadOrDying()) {
            dropOrDiscard(world);
            return;
        }

        if (tickCount > MAX_LIFETIME) {
            dropOrDiscard(world);
            return;
        }

        DiscStats spec = stats();
        if (!returning && (outwardTicks >= spec.flightTicks() + bounces * 5
                || travelled > spec.maxRange())) {
            returning = true;
        }

        if (returning) {
            Vec3 from = position();
            Vec3 home = owner.getEyePosition().add(0, -.30, 0);
            Vec3 homeVector = home.subtract(from);
            if (homeVector.lengthSqr() < 1.15) {
                returnTo(owner);
                return;
            }
            // Curved homing arc, rather than a sudden instant teleport home.
            Vec3 desired = homeVector.normalize();
            flight = flight.scale(.58).add(desired.scale(.42)).normalize();
            double speed = spec.speed() * 1.22;
            Vec3 next = from.add(flight.scale(Math.min(speed, homeVector.length())));
            damageEntities(world, owner, from, next, spec);
            setPos(next.x, next.y, next.z);
            syncDisplay(world);
            return;
        }

        outwardTicks++;
        Vec3 from = position();
        Vec3 to = from.add(flight.scale(spec.speed()));
        if (!world.hasChunkAt(BlockPos.containing(to))) {
            returning = true;
            syncDisplay(world);
            return;
        }

        BlockHitResult collision = world.clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        boolean blockHit = collision.getType() == HitResult.Type.BLOCK;
        Vec3 endpoint = blockHit ? collision.getLocation() : to;

        damageEntities(world, owner, from, endpoint, spec);
        travelled += from.distanceTo(endpoint);
        setPos(endpoint.x, endpoint.y, endpoint.z);

        if (blockHit) {
            // Triggers vanilla target blocks and other projectile-sensitive blocks.
            world.getBlockState(collision.getBlockPos()).onProjectileHit(
                    world, world.getBlockState(collision.getBlockPos()), collision, this);
            world.playSound(null, endpoint.x, endpoint.y, endpoint.z,
                    SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, .55f, 1.4f);
            if (bounces >= spec.maxBounces()) {
                returning = true;
            } else {
                bounces++;
                Direction face = collision.getDirection();
                Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
                flight = reflect(flight, normal).normalize();
                // Move away from the struck face to avoid hitting it every tick.
                Vec3 away = endpoint.add(normal.scale(.14));
                setPos(away.x, away.y, away.z);
            }
        }

        if (pierced >= spec.maxTargets()) returning = true;
        syncDisplay(world);
    }

    /**
     * Sweep between the current and next position; sort contacts by distance.
     * Each victim may be damaged at most once, even on ricochet or return.
     */
    private void damageEntities(ServerLevel world, ServerPlayer owner,
                                Vec3 from, Vec3 to, DiscStats spec) {
        List<Hit> hits = new ArrayList<>();
        for (LivingEntity candidate : world.getEntitiesOfClass(
                LivingEntity.class, new AABB(from, to).inflate(.72), e ->
                        e.isAlive() && !e.getUUID().equals(throwerId)
                        && !e.isSpectator() && !alreadyHit.contains(e.getUUID()))) {
            candidate.getBoundingBox().inflate(ENTITY_RADIUS).clip(from, to)
                    .ifPresent(at -> hits.add(new Hit(candidate, from.distanceToSqr(at))));
        }
        hits.sort(Comparator.comparingDouble(Hit::distance));
        for (Hit hit : hits) {
            if (pierced >= spec.maxTargets()) break;
            LivingEntity victim = hit.entity();
            alreadyHit.add(victim.getUUID());
            pierced++;
            if (victim.hurtServer(world, world.damageSources().thrown(this, owner), spec.damage())) {
                victim.push(flight.x * .24, .09, flight.z * .24);
                world.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                        SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, .55f, 1.6f);
            }
        }
    }

    static Vec3 reflect(Vec3 vector, Vec3 normal) {
        return vector.subtract(normal.scale(2 * vector.dot(normal)));
    }

    private void syncDisplay(ServerLevel world) {
        Display.ItemDisplay display = displayId == null ? null :
                (world.getEntity(displayId) instanceof Display.ItemDisplay d ? d : null);
        if (display == null) {
            display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, world);
            display.setNoGravity(true);
            display.setInvulnerable(true);
            display.setRequiresPrecisePosition(true);
            display.getSlot(0).set(getItem().copy());
            display.setPos(getX(), getY(), getZ());
            if (!world.addFreshEntity(display)) return;
            displayId = display.getUUID();
        }
        display.setPos(getX(), getY(), getZ());
        // Disc's XY sprite lies nearly horizontal, spinning with slight wobble.
        display.setXRot(83 + (float) Math.sin(tickCount * .34) * 6);
        display.setYRot((tickCount * 40) % 360);
        display.setDeltaMovement(Vec3.ZERO);
    }

    private void cleanupDisplay(ServerLevel world) {
        if (displayId == null) return;
        Entity e = world.getEntity(displayId);
        if (e != null) e.discard();
        displayId = null;
    }

    private void returnTo(ServerPlayer owner) {
        if (delivered) return;
        delivered = true;
        ServerLevel world = (ServerLevel) level();
        cleanupDisplay(world);
        world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, .7f, 1.3f);
        if (!creative) {
            ItemStack stack = getItem().copy();
            if (offHand && owner.getOffhandItem().isEmpty()) {
                owner.setItemInHand(InteractionHand.OFF_HAND, stack);
            } else if (!offHand && owner.getMainHandItem().isEmpty()) {
                owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
            } else if (!owner.addItem(stack) && !stack.isEmpty()) {
                owner.drop(stack, false);
            }
        }
        discard();
    }

    private void dropOrDiscard(ServerLevel world) {
        if (delivered) return;
        delivered = true;
        cleanupDisplay(world);
        if (!creative && !getItem().isEmpty()) {
            ItemEntity item = new ItemEntity(world, getX(), getY(), getZ(), getItem().copy());
            item.setPickUpDelay(15);
            world.addFreshEntity(item);
        }
        discard();
    }

    @Override
    public void onRemoval(Entity.RemovalReason reason) {
        if (level() instanceof ServerLevel world) cleanupDisplay(world);
        super.onRemoval(reason);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (throwerId != null) output.putString("DiscThrower", throwerId.toString());
        output.putDouble("DiscDirX", flight.x);
        output.putDouble("DiscDirY", flight.y);
        output.putDouble("DiscDirZ", flight.z);
        output.putDouble("DiscTravel", travelled);
        output.putInt("DiscOutTicks", outwardTicks);
        output.putInt("DiscBounces", bounces);
        output.putInt("DiscPierced", pierced);
        output.putBoolean("DiscReturning", returning);
        output.putBoolean("DiscOffHand", offHand);
        output.putBoolean("DiscCreative", creative);
        output.putBoolean("DiscDelivered", delivered);
        output.putString("DiscHits", String.join(",", alreadyHit.stream().map(UUID::toString).toList()));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        try {
            String ownerString = input.getStringOr("DiscThrower", "");
            if (!ownerString.isEmpty()) throwerId = UUID.fromString(ownerString);
        } catch (IllegalArgumentException ignored) { /* malformed old save */ }
        flight = new Vec3(
                input.getDoubleOr("DiscDirX", 0),
                input.getDoubleOr("DiscDirY", 0),
                input.getDoubleOr("DiscDirZ", 1));
        if (flight.lengthSqr() < .001) flight = new Vec3(0, 0, 1);
        flight = flight.normalize();
        travelled = input.getDoubleOr("DiscTravel", 0);
        outwardTicks = input.getIntOr("DiscOutTicks", 0);
        bounces = input.getIntOr("DiscBounces", 0);
        pierced = input.getIntOr("DiscPierced", 0);
        returning = input.getBooleanOr("DiscReturning", false);
        offHand = input.getBooleanOr("DiscOffHand", false);
        creative = input.getBooleanOr("DiscCreative", false);
        delivered = input.getBooleanOr("DiscDelivered", false);
        for (String s : input.getStringOr("DiscHits", "").split(",")) {
            try { if (!s.isBlank()) alreadyHit.add(UUID.fromString(s)); }
            catch (IllegalArgumentException ignored) { }
        }
    }

    private record Hit(LivingEntity entity, double distance) { }
}
