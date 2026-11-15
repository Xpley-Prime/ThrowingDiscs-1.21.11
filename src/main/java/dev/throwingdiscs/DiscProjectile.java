package dev.throwingdiscs;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import dev.throwingdiscs.mixin.DisplayInterpolationInvoker;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative returning disc with swept collision and item-display
 * rendering. Per-disc abilities are driven exclusively by item enchantments.
 */
public final class DiscProjectile extends ThrowableItemProjectile {
    private static final int MAX_LIFETIME = 240;
    private static final double ENTITY_RADIUS = .32;
    private static final double WIND_SEARCH_RADIUS = 12.0;
    private static final int MAX_WIND_DASH_TICKS = 17;

    private Vec3 flight = new Vec3(0, 0, 1);
    private double travelled;
    private int outwardTicks;
    private int bounces;
    private int pierced;
    private boolean returning;
    private boolean offHand;
    private boolean creative;
    private boolean delivered;

    private boolean windUsed;
    private boolean windDashing;
    private int windDashTicks;
    private UUID windTargetId;

    /** Multiple complete item stacks may be carried by the Adhesion enchantment. */
    private List<ItemStack> cargo = new ArrayList<>();
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
        Vec3 origin = owner.getEyePosition().add(this.flight.scale(.75)).add(0, -.13, 0);
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

    private int enchant(ServerLevel world, net.minecraft.resources.ResourceKey<
                        net.minecraft.world.item.enchantment.Enchantment> key) {
        return DiscEnchantments.level(world, getItem(), key);
    }

    @Override
    public void tick() {
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
        int adhesion = enchant(world, DiscEnchantments.ADHESION);
        int ricochet = enchant(world, DiscEnchantments.RICOCHET);
        int maxBounces = spec.maxBounces() + ricochet;

        if (windDashing) {
            tickWindDash(world, owner, spec, adhesion > 0);
            syncDisplay(world);
            return;
        }

        if (!returning && (outwardTicks >= spec.flightTicks() + bounces * 5
                || travelled >= spec.maxRange())) returning = true;

        if (returning) {
            Vec3 from = position();
            Vec3 home = owner.getEyePosition().add(0, -.30, 0);
            Vec3 toHome = home.subtract(from);
            if (toHome.lengthSqr() < 1.15) {
                returnTo(owner);
                return;
            }
            Vec3 desired = toHome.normalize();
            flight = flight.scale(.58).add(desired.scale(.42)).normalize();
            Vec3 next = from.add(flight.scale(Math.min(spec.speed() * 1.22, toHome.length())));
            if (adhesion > 0) collectItems(world, from, next);
            damageEntities(world, owner, from, next, spec, false);
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

        if (adhesion > 0) collectItems(world, from, endpoint);
        damageEntities(world, owner, from, endpoint, spec, false);
        travelled += from.distanceTo(endpoint);
        setPos(endpoint.x, endpoint.y, endpoint.z);

        // A Wind Burst hit always takes priority over the usual ricochet.
        if (windDashing) {
            syncDisplay(world);
            return;
        }

        if (blockHit) {
            world.getBlockState(collision.getBlockPos()).onProjectileHit(
                    world, world.getBlockState(collision.getBlockPos()), collision, this);
            world.playSound(null, endpoint.x, endpoint.y, endpoint.z,
                    SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, .55f, 1.4f);
            if (bounces >= maxBounces) {
                returning = true;
            } else {
                bounces++;
                Direction face = collision.getDirection();
                Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
                flight = reflect(flight, normal).normalize();
                Vec3 away = endpoint.add(normal.scale(.14));
                setPos(away.x, away.y, away.z);
            }
        }

        if (pierced >= spec.maxTargets()) returning = true;
        syncDisplay(world);
    }

    /**
     * The wind-assisted second flight is fast, homes toward the nearest OTHER
     * mob, then returns to its owner after that hit or a short timeout.
     */
    private void tickWindDash(ServerLevel world, ServerPlayer owner,
                              DiscStats spec, boolean sticky) {
        Entity targetEntity = windTargetId == null ? null : world.getEntity(windTargetId);
        if (!(targetEntity instanceof LivingEntity target) || !target.isAlive()
                || ++windDashTicks > MAX_WIND_DASH_TICKS) {
            windDashing = false;
            returning = true;
            return;
        }

        Vec3 from = position();
        Vec3 goal = target.getBoundingBox().getCenter();
        Vec3 delta = goal.subtract(from);
        if (delta.lengthSqr() < .36) {
            strike(world, owner, target, spec);
            windDashing = false;
            returning = true;
            return;
        }
        flight = delta.normalize();
        double speed = Math.max(1.8, spec.speed() * 2.3);
        Vec3 to = from.add(flight.scale(Math.min(speed, delta.length())));
        if (!world.hasChunkAt(BlockPos.containing(to))) {
            windDashing = false;
            returning = true;
            return;
        }
        BlockHitResult block = world.clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 end = block.getType() == HitResult.Type.BLOCK ? block.getLocation() : to;

        if (sticky) collectItems(world, from, end);
        boolean hitTarget = target.getBoundingBox().inflate(ENTITY_RADIUS).clip(from, end).isPresent()
                || target.getBoundingBox().inflate(ENTITY_RADIUS).contains(end);
        setPos(end.x, end.y, end.z);
        if (hitTarget) {
            strike(world, owner, target, spec);
            windDashing = false;
            returning = true;
        } else if (block.getType() == HitResult.Type.BLOCK) {
            windDashing = false;
            returning = true;
        }
    }

    private void damageEntities(ServerLevel world, ServerPlayer owner, Vec3 from,
                                Vec3 to, DiscStats spec, boolean forcedReturn) {
        List<Hit> contacts = new ArrayList<>();
        for (LivingEntity entity : world.getEntitiesOfClass(LivingEntity.class,
                new AABB(from, to).inflate(.72),
                e -> e.isAlive() && !e.isSpectator()
                        && !e.getUUID().equals(throwerId)
                        && !alreadyHit.contains(e.getUUID()))) {
            entity.getBoundingBox().inflate(ENTITY_RADIUS).clip(from, to)
                    .ifPresent(at -> contacts.add(new Hit(entity, from.distanceToSqr(at))));
        }
        contacts.sort(Comparator.comparingDouble(Hit::distance));
        for (Hit hit : contacts) {
            if (pierced >= spec.maxTargets()) break;
            LivingEntity victim = hit.entity();
            strike(world, owner, victim, spec);
            // Wind Burst activates once and interrupts the ordinary piercing path.
            if (windUsed || forcedReturn) break;
        }
    }

    private void strike(ServerLevel world, ServerPlayer owner,
                        LivingEntity victim, DiscStats spec) {
        if (!alreadyHit.add(victim.getUUID())) return;
        pierced++;

        var source = world.damageSources().thrown(this, owner);
        // Sharpness, Smite, Bane of Arthropods and other vanilla damage
        // modifiers are applied to the thrown item, not the now-empty hand.
        float damage = EnchantmentHelper.modifyDamage(world, getItem(), victim, source, spec.damage());
        if (victim.hurtServer(world, source, damage)) {
            int customKnockback = enchant(world, DiscEnchantments.KNOCKBACK);
            float vanillaKnockback = EnchantmentHelper.modifyKnockback(world, getItem(), victim, source, 0);
            double shove = .24 + customKnockback * .46 + vanillaKnockback * .35;
            victim.push(flight.x * shove, .09 + customKnockback * .10, flight.z * shove);
            victim.hurtMarked = true;

            int fire = enchant(world, Enchantments.FIRE_ASPECT);
            if (fire > 0) victim.igniteForSeconds(fire * 4);

            world.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                    SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, .55f, 1.6f);

            int wind = enchant(world, Enchantments.WIND_BURST);
            if (!windUsed && wind > 0) triggerWindBurst(world, owner, victim, wind);
        }
    }

    private void triggerWindBurst(ServerLevel world, ServerPlayer owner,
                                  LivingEntity first, int enchantLevel) {
        windUsed = true;
        Vec3 origin = first.getBoundingBox().getCenter();
        world.sendParticles(ParticleTypes.GUST_EMITTER_LARGE,
                origin.x, origin.y, origin.z, 1, 0, 0, 0, 0);
        world.playSound(null, origin.x, origin.y, origin.z,
                SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1f, 1f);

        double radius = 2.2 + .6 * enchantLevel;
        double power = .65 + .20 * enchantLevel;
        for (LivingEntity affected : world.getEntitiesOfClass(LivingEntity.class,
                new AABB(origin, origin).inflate(radius),
                e -> e.isAlive() && e != owner && e.distanceToSqr(origin) <= radius * radius)) {
            Vec3 out = affected.getBoundingBox().getCenter().subtract(origin);
            if (out.lengthSqr() < .002) out = new Vec3(0, 1, 0);
            Vec3 push = out.normalize().scale(power);
            affected.push(push.x, Math.max(.22, push.y + .24), push.z);
            affected.hurtMarked = true;
        }

        // Real retargeting: only another mob, never the initial victim or owner.
        List<Mob> nearby = world.getEntitiesOfClass(Mob.class,
                new AABB(origin, origin).inflate(WIND_SEARCH_RADIUS),
                m -> m.isAlive() && !alreadyHit.contains(m.getUUID())
                        && m.distanceToSqr(origin) <= WIND_SEARCH_RADIUS * WIND_SEARCH_RADIUS);
        nearby.sort(Comparator.comparingDouble(m -> m.distanceToSqr(origin)));
        if (nearby.isEmpty()) {
            returning = true;
            return;
        }

        windTargetId = nearby.get(0).getUUID();
        windDashing = true;
        windDashTicks = 0;
        returning = false;
    }

    /** Collect every touched stack on both outward and return flights. */
    private void collectItems(ServerLevel world, Vec3 from, Vec3 to) {
        List<ItemEntity> touched = world.getEntitiesOfClass(ItemEntity.class,
                new AABB(from, to).inflate(.55),
                e -> !e.isRemoved() && !e.getItem().isEmpty());
        for (ItemEntity item : touched) {
            AABB bounds = item.getBoundingBox().inflate(.32);
            if (!bounds.contains(from) && !bounds.contains(to)
                    && bounds.clip(from, to).isEmpty()) continue;
            cargo.add(item.getItem().copy());
            item.discard();
            world.playSound(null, item.getX(), item.getY(), item.getZ(),
                    SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, .45f, 1.3f);
        }
    }

    private void deliverCargo(ServerPlayer owner) {
        for (ItemStack original : cargo) {
            ItemStack stack = original.copy();
            owner.addItem(stack);
            if (!stack.isEmpty()) owner.drop(stack, false);
        }
        cargo.clear();
    }

    private void dropCargo(ServerLevel world) {
        for (ItemStack stack : cargo) {
            if (stack.isEmpty()) continue;
            ItemEntity drop = new ItemEntity(world, getX(), getY(), getZ(), stack.copy());
            drop.setPickUpDelay(5);
            world.addFreshEntity(drop);
        }
        cargo.clear();
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
            // Vanilla item_display normally snaps to each server-tick position.
            // Keep 2 ticks of client-side blending for both position and yaw/pitch;
            // the physics/hitbox of DiscProjectile remain server-authoritative.
            ((DisplayInterpolationInvoker) display).throwingdiscs$setPosRotInterpolationDuration(2);
            display.getSlot(0).set(getItem().copy());
            display.setPos(getX(), getY(), getZ());
            if (!world.addFreshEntity(display)) return;
            displayId = display.getUUID();
        }
        display.setPos(getX(), getY(), getZ());
        display.setXRot(83 + (float) Math.sin(tickCount * .34) * 6);
        // 32 degrees/tick; vanilla angle interpolation fills in frames between ticks.
        display.setYRot((tickCount * 32) % 360);
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
            } else {
                owner.addItem(stack);
                if (!stack.isEmpty()) owner.drop(stack, false);
            }
        }
        deliverCargo(owner);
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
        dropCargo(world);
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
        output.putBoolean("WindUsed", windUsed);
        output.putBoolean("WindDashing", windDashing);
        output.putInt("WindDashTicks", windDashTicks);
        if (windTargetId != null) output.putString("WindTarget", windTargetId.toString());
        output.putString("DiscHits", String.join(",", alreadyHit.stream().map(UUID::toString).toList()));
        if (!cargo.isEmpty()) output.store("DiscCargo", ItemStack.CODEC.listOf(), cargo);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        try {
            String ownerString = input.getStringOr("DiscThrower", "");
            if (!ownerString.isEmpty()) throwerId = UUID.fromString(ownerString);
        } catch (IllegalArgumentException ignored) {}
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
        windUsed = input.getBooleanOr("WindUsed", false);
        windDashing = input.getBooleanOr("WindDashing", false);
        windDashTicks = input.getIntOr("WindDashTicks", 0);
        try {
            String target = input.getStringOr("WindTarget", "");
            if (!target.isEmpty()) windTargetId = UUID.fromString(target);
        } catch (IllegalArgumentException ignored) {}
        cargo = new ArrayList<>(input.read("DiscCargo", ItemStack.CODEC.listOf()).orElse(List.of()));
        // Older 2.2 discs used a single ItemStack for cargo.
        if (cargo.isEmpty()) input.read("DiscCargo", ItemStack.CODEC).ifPresent(cargo::add);
        for (String s : input.getStringOr("DiscHits", "").split(",")) {
            try { if (!s.isBlank()) alreadyHit.add(UUID.fromString(s)); }
            catch (IllegalArgumentException ignored) {}
        }
    }

    private record Hit(LivingEntity entity, double distance) {}
}
