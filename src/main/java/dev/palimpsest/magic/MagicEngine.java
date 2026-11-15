package dev.palimpsest.magic;

import dev.palimpsest.Palimpsest;
import dev.palimpsest.glyph.Glyph;
import dev.palimpsest.net.ManaPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Fully server-authoritative spell grammar, ink economy, living seals and echo queue. */
public final class MagicEngine {
    private static final Map<UUID,Float> INK = new HashMap<>();
    private static final Map<UUID,Long> LAST_CAST = new HashMap<>();
    private static final List<Pending> PENDING = new ArrayList<>();
    private static final List<Seal> SEALS = new ArrayList<>();
    private static long tick;

    private record Pending(long due, UUID owner, Glyph intent, Glyph element, boolean inverted, float power) {}
    private static final class Seal {
        final UUID owner;
        final ServerLevel level;
        final Vec3 pos;
        final Glyph element;
        final boolean inverted;
        final float power;
        final long expiry;
        final long armed;
        Seal(ServerPlayer player, Vec3 pos, Glyph element, boolean inverted, float power) {
            this.owner=player.getUUID();this.level=(ServerLevel)player.level();this.pos=pos;
            this.element=element;this.inverted=inverted;this.power=power;
            this.expiry=tick+160;this.armed=tick+15;
        }
    }
    private MagicEngine() {}

    public static java.util.Map<UUID,Float> snapshotInk(){return java.util.Map.copyOf(INK);}
    public static void loadInk(java.util.Map<UUID,Float> data){
        INK.clear();data.forEach((id,value)->INK.put(id,Math.clamp(value,0f,100f)));
        LAST_CAST.clear();PENDING.clear();SEALS.clear();tick=0;
    }
    public static float mana(ServerPlayer player) { return INK.getOrDefault(player.getUUID(),100f); }
    public static boolean spend(ServerPlayer player,int amount) {
        if(player.getAbilities().instabuild || amount<=0)return true;
        if(mana(player)<amount)return false;
        INK.put(player.getUUID(),mana(player)-amount);
        ServerPlayNetworking.send(player,new ManaPayload(Math.round(mana(player))));
        return true;
    }
    public static void refill(ServerPlayer player,float amount) {
        INK.put(player.getUUID(),Math.min(100,Math.max(0,mana(player)+amount)));
        ServerPlayNetworking.send(player,new ManaPayload(Math.round(mana(player))));
    }


    public static void cast(ServerPlayer player,Glyph intent,Glyph element,Glyph modifier,float calligraphy) {
        if (!player.getMainHandItem().is(Palimpsest.QUILL) &&
            !player.getOffhandItem().is(Palimpsest.QUILL)) {
            notify(player,"§cНужно держать Перо Палимпсеста."); return;
        }
        if(intent==null || intent.role()!=Glyph.Role.INTENT ||
            element==null || element.role()!=Glyph.Role.ELEMENT ||
            (modifier!=null && modifier.role()!=Glyph.Role.INFLECTION)) return;
        if(LAST_CAST.getOrDefault(player.getUUID(),-99L)+12>tick) {
            notify(player,"§7Линии ещё дрожат — подожди немного."); return;
        }
        int cost = 12 + (intent==Glyph.HALO ? 8 : 0)
            + (modifier==Glyph.ECHO ? 9 : modifier==Glyph.BLOOD ? 17 : modifier==Glyph.INVERT ? 6 : 0);
        float current=mana(player);
        if(current<cost && !player.getAbilities().instabuild) {
            notify(player,"§cНе хватает чернил: "+Math.round(current)+" / "+cost);return;
        }
        INK.put(player.getUUID(),Math.max(0,current-cost));
        LAST_CAST.put(player.getUUID(),tick);

        boolean inverted=modifier==Glyph.INVERT;
        // The actual handwriting quality affects physical power; no user-supplied strength value.
        float precision=0.72f+0.42f*Math.max(.0f,Math.min(1f,calligraphy));
        float strength=(modifier==Glyph.BLOOD ? 2.0f : 1.0f) * resonance(player,element) * precision;
        apply(player,intent,element,inverted,strength);
        if(modifier==Glyph.ECHO) PENDING.add(new Pending(tick+27,player.getUUID(),intent,element,inverted,strength*.75f));
        if(modifier==Glyph.BLOOD && !player.getAbilities().invulnerable)
            player.hurtServer((ServerLevel)player.level(),player.damageSources().magic(),2f);

        ((ServerLevel)player.level()).playSound(null,player.blockPosition(),
            SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.PLAYERS,.9f,
            .8f+(((intent.ordinal()+element.ordinal())%5)*.12f));
        notify(player,"§d"+intent.display()+" §8· §b"+element.display()+
            (modifier==null ? "" : " §8· §6"+modifier.display())+
            " §7["+cost+" чернил]");
        ServerPlayNetworking.send(player,new ManaPayload(Math.round(mana(player))));
    }

    private static void apply(ServerPlayer player,Glyph intent,Glyph element,boolean inverted,float power) {
        ServerLevel level=(ServerLevel)player.level();
        if(intent==Glyph.HALO) {
            Vec3 at=lookAtBlock(player);
            SEALS.add(new Seal(player,at,element,inverted,power));
            particles(level,at,element,20,1.0);
        } else if(intent==Glyph.NEEDLE) {
            LivingEntity target=findInSight(player,16);
            Vec3 where=target==null ? lookAtBlock(player) : target.position().add(0,target.getBbHeight()*.5,0);
            if(target!=null) affect(level,target,element,inverted,power);
            else affectBlock(level,BlockPos.containing(where),element);
            particles(level,where,element,38,.55);
            // A segmented visual trail between the wand and impact.
            Vec3 start=player.getEyePosition();
            Vec3 line=where.subtract(start);
            for(int i=1;i<=18;i++) {
                Vec3 p=start.add(line.scale(i/19.0));
                level.sendParticles(visual(element),p.x,p.y,p.z,1,0,0,0,0);
            }
        } else if(intent==Glyph.RIPPLE) {
            Vec3 where=player.position();
            for(LivingEntity entity:level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(5), e->e.isAlive() && e!=player))
                affect(level,entity,element,inverted,power*.8f);
            particles(level,where.add(0,.8,0),element,90,3);
        }
    }

    private static void affect(ServerLevel level,LivingEntity entity,Glyph element,boolean inverted,float power) {
        if(!inverted) {
            switch(element) {
                case CINDER -> {
                    entity.hurtServer(level,level.damageSources().magic(),3.0f*power);
                    entity.igniteForSeconds(Math.max(1,Math.round(3*power)));
                }
                case BLOOM -> {
                    entity.heal(3.5f*power);
                    entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION,80,0));
                }
                case RIME -> {
                    entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,120,Math.min(2,(int)power)));
                    entity.setTicksFrozen(Math.min(160,entity.getTicksFrozen()+Math.round(70*power)));
                }
                case VEIL -> {
                    entity.addEffect(new MobEffectInstance(MobEffects.LEVITATION,45,0));
                    entity.addEffect(new MobEffectInstance(MobEffects.GLOWING,120,0));
                }
                default -> {}
            }
        } else {
            switch(element) {
                case CINDER -> {
                    entity.clearFire();
                    entity.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,160,0));
                }
                case BLOOM -> entity.hurtServer(level,level.damageSources().magic(),2.7f*power);
                case RIME -> {
                    entity.setTicksFrozen(0);
                    entity.addEffect(new MobEffectInstance(MobEffects.SPEED,140,0));
                }
                case VEIL -> {
                    entity.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,180,0));
                    entity.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,100,0));
                }
                default -> {}
            }
        }
    }

    private static void affectBlock(ServerLevel level,BlockPos pos,Glyph element) {
        if(element!=Glyph.BLOOM) return;
        // Draw growth into the world instead of merely applying status effects.
        BlockState state=level.getBlockState(pos);
        if(state.getBlock() instanceof BonemealableBlock plant && plant.isValidBonemealTarget(level,pos,state)) {
            if(plant.isBonemealSuccess(level,level.random,pos,state))
                plant.performBonemeal(level,level.random,pos,state);
        }
    }

    private static Vec3 lookAtBlock(ServerPlayer player) {
        HitResult hit=player.pick(15,0,false);
        if(hit instanceof BlockHitResult block && hit.getType()==HitResult.Type.BLOCK)
            return Vec3.atCenterOf(block.getBlockPos().relative(block.getDirection()));
        return player.getEyePosition().add(player.getLookAngle().scale(10));
    }

    private static LivingEntity findInSight(ServerPlayer player,int range) {
        ServerLevel level=(ServerLevel)player.level();
        Vec3 origin=player.getEyePosition(),dir=player.getLookAngle();
        double closest=range+1;
        LivingEntity best=null;
        for(LivingEntity entity:level.getEntitiesOfClass(LivingEntity.class,
            player.getBoundingBox().inflate(range),e->e.isAlive() && e!=player)) {
            Vec3 delta=entity.position().add(0,entity.getBbHeight()*.5,0).subtract(origin);
            double along=delta.dot(dir);
            if(along<0 || along>range || along>=closest) continue;
            double offset=delta.subtract(dir.scale(along)).length();
            if(offset <= Math.max(.85,entity.getBbWidth()*.75)) {
                // Do not target creatures behind walls.
                HitResult block=level.clip(new net.minecraft.world.level.ClipContext(origin,
                    origin.add(dir.scale(along)),net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,player));
                if(block.getType()==HitResult.Type.MISS) {closest=along;best=entity;}
            }
        }
        return best;
    }

    private static float resonance(ServerPlayer player,Glyph element) {
        ServerLevel level=(ServerLevel)player.level();
        return switch(element) {
            case CINDER -> level.getDayTime()%24000<12000 && level.canSeeSky(player.blockPosition()) ? 1.25f:1f;
            case BLOOM -> level.isRainingAt(player.blockPosition()) ? 1.35f:1f;
            case RIME -> level.isRainingAt(player.blockPosition()) || player.isInWater() ? 1.35f:1f;
            case VEIL -> level.getDayTime()%24000>=13000 ? 1.4f:1f;
            default -> 1f;
        };
    }

    private static ParticleOptions visual(Glyph element) {
        return switch(element) {
            case CINDER -> ParticleTypes.FLAME;
            case BLOOM -> ParticleTypes.HAPPY_VILLAGER;
            case RIME -> ParticleTypes.SNOWFLAKE;
            case VEIL -> ParticleTypes.PORTAL;
            default -> ParticleTypes.ENCHANT;
        };
    }
    private static void particles(ServerLevel level,Vec3 at,Glyph element,int amount,double spread) {
        level.sendParticles(visual(element),at.x,at.y,at.z,amount,spread,spread*.5,spread,.045);
    }
    private static void notify(ServerPlayer player,String message) {
        player.displayClientMessage(Component.literal(message),true);
    }

    public static void tick(MinecraftServer server) {
        tick++;
        for(ServerPlayer player:server.getPlayerList().getPlayers()) {
            float before=mana(player);
            INK.put(player.getUUID(),Math.min(100,before+.20f));
            if(tick%20==0) ServerPlayNetworking.send(player,new ManaPayload(Math.round(mana(player))));
        }
        for(Iterator<Pending> it=PENDING.iterator();it.hasNext();) {
            Pending pending=it.next();
            if(pending.due<=tick) {
                it.remove();
                ServerPlayer player=server.getPlayerList().getPlayer(pending.owner());
                if(player!=null) apply(player,pending.intent(),pending.element(),pending.inverted(),pending.power());
            }
        }
        for(Iterator<Seal> it=SEALS.iterator();it.hasNext();) {
            Seal seal=it.next();
            if(tick>seal.expiry) { it.remove();continue; }
            if(tick%8==0) {
                for(int i=0;i<16;i++) {
                    double a=i*2*Math.PI/16;
                    seal.level.sendParticles(visual(seal.element),seal.pos.x+Math.cos(a)*1.5,
                        seal.pos.y+.2,seal.pos.z+Math.sin(a)*1.5,1,0,0,0,0);
                }
            }
            if(tick<seal.armed || tick%5!=0) continue;
            ServerPlayer owner=server.getPlayerList().getPlayer(seal.owner);
            if(owner==null || owner.level()!=seal.level) {it.remove();continue;}
            List<LivingEntity> nearby=seal.level.getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(seal.pos,3.5,3.5,3.5),
                e->e.isAlive() && e!=owner && e.distanceToSqr(seal.pos)<4.0);
            if(!nearby.isEmpty()) {
                for(LivingEntity target:nearby) affect(seal.level,target,seal.element,seal.inverted,seal.power);
                particles(seal.level,seal.pos,seal.element,56,1.6);
                it.remove();
            }
        }
    }
}
