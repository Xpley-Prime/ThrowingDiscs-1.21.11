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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The handwritten language is executed by the server only.
 * INTENT chooses spatial operation; ELEMENT changes world and beings;
 * INFLECTION changes magnitude, duration, direction or persistence.
 */
public final class MagicEngine {
    private static final Map<UUID,Float> INK=new HashMap<>();
    private static final Map<UUID,Long> LAST_CAST=new HashMap<>();
    private static final List<Pending> PENDING=new ArrayList<>();
    private static final List<Seal> SEALS=new ArrayList<>();
    private static long tick;

    private record Pending(long due,UUID owner,Glyph intent,Glyph element,float power){}
    private static final class Seal {
        final UUID owner;
        final ServerLevel level;
        final Vec3 pos;
        final Glyph element,modifier;
        final float power;
        final long expiry,armed;
        Seal(ServerPlayer player,Vec3 pos,Glyph element,Glyph modifier,float power){
            this.owner=player.getUUID();this.level=(ServerLevel)player.level();
            this.pos=pos;this.element=element;this.modifier=modifier;this.power=power;
            this.expiry=tick+(modifier==Glyph.SUSTAIN || modifier==Glyph.ANCHOR?420:180);
            this.armed=tick+12;
        }
    }
    private MagicEngine(){}

    public static Map<UUID,Float> snapshotInk(){return Map.copyOf(INK);}
    public static void loadInk(Map<UUID,Float> data){
        INK.clear();data.forEach((id,n)->INK.put(id,Math.clamp(n,0f,100f)));
        LAST_CAST.clear();PENDING.clear();SEALS.clear();tick=0;
    }
    public static float mana(ServerPlayer player){return INK.getOrDefault(player.getUUID(),100f);}
    public static boolean spend(ServerPlayer player,int amount){
        if(amount<=0 || player.getAbilities().instabuild)return true;
        if(mana(player)<amount)return false;
        INK.put(player.getUUID(),mana(player)-amount);
        ServerPlayNetworking.send(player,new ManaPayload(Math.round(mana(player))));
        return true;
    }
    public static void refill(ServerPlayer player,float amount){
        INK.put(player.getUUID(),Math.clamp(mana(player)+amount,0f,100f));
        ServerPlayNetworking.send(player,new ManaPayload(Math.round(mana(player))));
    }

    public static void cast(ServerPlayer player,Glyph intent,Glyph element,Glyph modifier,float calligraphy){
        if(!player.getMainHandItem().is(Palimpsest.QUILL)&&
            !player.getOffhandItem().is(Palimpsest.QUILL)){
            say(player,"§cВозьми перо в любую руку.");return;
        }
        if(intent==null||intent.role()!=Glyph.Role.INTENT ||
            element==null||element.role()!=Glyph.Role.ELEMENT||
            (modifier!=null&&modifier.role()!=Glyph.Role.INFLECTION))return;
        if(LAST_CAST.getOrDefault(player.getUUID(),-100L)+14>tick){
            say(player,"§7Магические чернила ещё не остыли.");return;
        }
        int cost=12+(intent==Glyph.HALO?7:0)+(intent==Glyph.SPIRE?7:0)+
            switch(modifier==null?Glyph.ECHO:modifier){
                case BLOOD->22;case ECHO->modifier==null?0:8;
                case INVERT->5;case FOCUS->5;case SPREAD->12;
                case SUSTAIN->9;case ANCHOR->12;case SILENCE->-5;
                default->0;
            };
        if(mana(player)<cost&&!player.getAbilities().instabuild){
            say(player,"§cНедостаточно чернил: "+Math.round(mana(player))+" / "+cost);return;
        }
        float quality=Math.clamp(calligraphy,0f,1f);
        float strength=(.75f+quality*.50f)*resonance(player,element);
        if(modifier==Glyph.BLOOD)strength*=2.15f;
        if(modifier==Glyph.FOCUS)strength*=1.55f;
        if(modifier==Glyph.SPREAD)strength*=.9f;
        if(modifier==Glyph.SUSTAIN)strength*=1.25f;
        if(!spend(player,cost))return;
        LAST_CAST.put(player.getUUID(),tick);
        apply(player,intent,element,modifier,strength);
        if(modifier==Glyph.ECHO) PENDING.add(new Pending(tick+28,player.getUUID(),intent,element,strength*.80f));
        if(modifier==Glyph.BLOOD && !player.getAbilities().invulnerable)
            player.hurtServer((ServerLevel)player.level(),player.damageSources().magic(),3);
        if(modifier!=Glyph.SILENCE){
            ServerLevel level=(ServerLevel)player.level();
            level.playSound(null,player.blockPosition(),SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS,1.8f,.66f+(element.ordinal()%7)*.09f);
            level.playSound(null,player.blockPosition(),SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS,.45f,.85f);
        }
        say(player,"§d"+intent.display()+" §7› §b"+element.display()+
            (modifier==null?"":" §7› §6"+modifier.display())+" §8[−"+cost+" чернил]");
    }

    private static void apply(ServerPlayer player,Glyph intent,Glyph element,Glyph modifier,float power){
        ServerLevel level=(ServerLevel)player.level();
        boolean invert=modifier==Glyph.INVERT;
        int radius=modifier==Glyph.SPREAD?3:modifier==Glyph.FOCUS?0:1;
        Vec3 hit=aim(player,16);
        BlockPos center=BlockPos.containing(hit);
        int worldChanged=0;
        switch(intent){
            case NEEDLE -> {
                LivingEntity target=findInSight(player,16);
                Vec3 end=target==null?hit:target.position().add(0,target.getBbHeight()*.5,0);
                if(target!=null)affect(level,target,element,invert,power);
                else worldChanged=WorldMagic.rewrite(level,center,element,invert,radius,power,player);
                beam(level,player.getEyePosition(),end,element);
                burst(level,end,element,modifier==Glyph.SILENCE?1:2,1.0);
            }
            case HALO -> {
                Vec3 sealAt=Vec3.atCenterOf(center);
                SEALS.add(new Seal(player,sealAt,element,modifier,power));
                if(SEALS.size()>256)SEALS.removeFirst();
                worldChanged=WorldMagic.rewrite(level,center,element,invert,Math.min(radius,1),power,player);
                circle(level,sealAt,element,1.65,48);
                burst(level,sealAt,element,2,1.5);
            }
            case RIPPLE -> {
                for(LivingEntity e:level.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(modifier==Glyph.SPREAD?8:5),
                    e->e.isAlive()&&e!=player)) affect(level,e,element,invert,power*.85f);
                worldChanged=WorldMagic.rewrite(level,player.blockPosition().below(),element,
                    invert,Math.max(1,radius),power,player);
                circle(level,player.position().add(0,.7,0),element,3.6,68);
                burst(level,player.position().add(0,1,0),element,2,2.4);
            }
            case FAN -> {
                int r=Math.min(4,radius+2);
                for(LivingEntity e:level.getEntitiesOfClass(LivingEntity.class,
                    AABB.ofSize(hit,2*r+1,5,2*r+1),LivingEntity::isAlive))
                    affect(level,e,element,invert,power*.85f);
                worldChanged=WorldMagic.rewrite(level,center,element,invert,Math.min(3,r),power,player);
                circle(level,hit,element,r+.2,72);
                burst(level,hit,element,3,r);
            }
            case BIND -> {
                Vec3 from=player.position().add(0,.7,0);
                Vec3 delta=hit.subtract(from);
                double length=Math.min(12,delta.length());
                Vec3 dir=delta.lengthSqr()>.0001?delta.normalize():new Vec3(0,0,1);
                Set<UUID> touched=new HashSet<>();
                for(int i=1;i<=9;i++){
                    Vec3 p=from.add(dir.scale(i*length/9));
                    BlockPos bp=BlockPos.containing(p);
                    worldChanged+=WorldMagic.rewrite(level,bp,element,invert,0,power,player);
                    for(LivingEntity e:level.getEntitiesOfClass(LivingEntity.class,
                        AABB.ofSize(p,2.4,2.4,2.4),e->e!=player&&e.isAlive()))
                        if(touched.add(e.getUUID())) affect(level,e,element,invert,power*.7f);
                }
                beam(level,from,from.add(dir.scale(length)),element);
                burst(level,hit,element,2,1.5);
            }
            case SPIRE -> {
                Vec3 top=hit.add(0,6,0);
                for(int y=0;y<5;y++){
                    BlockPos at=center.above(y);
                    worldChanged+=WorldMagic.rewrite(level,at,element,invert,0,power,player);
                }
                for(LivingEntity e:level.getEntitiesOfClass(LivingEntity.class,
                    AABB.ofSize(hit.add(0,2.5,0),3,7,3),LivingEntity::isAlive))
                    affect(level,e,element,invert,power);
                beam(level,hit,top,element);
                burst(level,hit.add(0,3,0),element,3,2.0);
            }
            default -> {}
        }
        if(modifier==Glyph.ANCHOR && intent!=Glyph.HALO){
            SEALS.add(new Seal(player,hit,element,modifier,power*.65f));
            if(SEALS.size()>256)SEALS.removeFirst();
            circle(level,hit,element,1.8,40);
        }
        if(worldChanged>0)level.sendParticles(ParticleTypes.END_ROD,hit.x,hit.y+.5,hit.z,
            Math.min(110,15+worldChanged*4),.9,.9,.9,.07);
    }

    private static void affect(ServerLevel level,LivingEntity entity,Glyph element,
                               boolean inverted,float power){
        int duration=Math.max(30,(int)(100*power));
        if(inverted){
            switch(element){
                case CINDER -> {entity.clearFire();entity.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,duration*2,0));}
                case BLOOM,ROOT -> entity.hurtServer(level,level.damageSources().magic(),3.5f*power);
                case RIME -> {entity.setTicksFrozen(0);entity.addEffect(new MobEffectInstance(MobEffects.SPEED,duration*2,1));}
                case VEIL,WIND -> entity.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,duration*2,0));
                case STONE,METAL -> entity.addEffect(new MobEffectInstance(MobEffects.SPEED,duration,1));
                case TIDE -> entity.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING,duration*2,0));
                case STORM -> entity.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,duration,0));
                case LIGHT -> entity.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,duration,0));
                case SHADOW -> entity.addEffect(new MobEffectInstance(MobEffects.GLOWING,duration*2,0));
                case WARP -> entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,duration,1));
                default -> {}
            }
            return;
        }
        switch(element){
            case CINDER -> {
                entity.hurtServer(level,level.damageSources().magic(),3.5f*power);
                entity.igniteForSeconds(Math.max(2,(int)(4*power)));
            }
            case BLOOM -> {
                entity.heal(4f*power);
                entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION,duration,0));
            }
            case RIME -> {
                entity.setTicksFrozen(Math.min(200,entity.getTicksFrozen()+(int)(80*power)));
                entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,duration,1));
            }
            case VEIL -> {
                entity.addEffect(new MobEffectInstance(MobEffects.LEVITATION,(int)(48*power),0));
                entity.addEffect(new MobEffectInstance(MobEffects.GLOWING,duration,0));
            }
            case STONE -> entity.addEffect(new MobEffectInstance(MobEffects.RESISTANCE,duration*2,0));
            case TIDE -> {entity.clearFire();entity.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING,duration*2,0));}
            case STORM -> entity.hurtServer(level,level.damageSources().magic(),5*power);
            case LIGHT -> {entity.heal(2*power);entity.addEffect(new MobEffectInstance(MobEffects.GLOWING,duration,0));}
            case SHADOW -> {
                entity.addEffect(new MobEffectInstance(MobEffects.DARKNESS,duration,0));
                entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,duration,0));
            }
            case WARP -> {
                Vec3 delta=new Vec3(level.random.nextDouble()*8-4,0,level.random.nextDouble()*8-4);
                Vec3 dest=entity.position().add(delta);
                BlockPos bp=BlockPos.containing(dest);
                if(level.isLoaded(bp)&&level.getBlockState(bp).isAir()&&
                    level.getBlockState(bp.above()).isAir())
                    entity.teleportTo(dest.x,dest.y,dest.z);
            }
            case ROOT -> entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION,duration*2,0));
            case METAL -> entity.addEffect(new MobEffectInstance(MobEffects.RESISTANCE,duration*2,1));
            case WIND -> entity.push((level.random.nextDouble()-.5)*power,.85*power,
                (level.random.nextDouble()-.5)*power);
            default -> {}
        }
    }

    private static Vec3 aim(ServerPlayer player,double reach){
        HitResult hit=player.pick(reach,0,false);
        if(hit instanceof BlockHitResult b && hit.getType()==HitResult.Type.BLOCK)
            return Vec3.atCenterOf(b.getBlockPos().relative(b.getDirection()));
        return player.getEyePosition().add(player.getLookAngle().scale(Math.min(reach,10)));
    }
    private static LivingEntity findInSight(ServerPlayer player,int reach){
        ServerLevel level=(ServerLevel)player.level();
        Vec3 origin=player.getEyePosition(),dir=player.getLookAngle();
        LivingEntity target=null;double closest=reach+1;
        for(LivingEntity entity:level.getEntitiesOfClass(LivingEntity.class,
            player.getBoundingBox().inflate(reach),e->e.isAlive()&&e!=player)){
            Vec3 delta=entity.position().add(0,entity.getBbHeight()*.5,0).subtract(origin);
            double along=delta.dot(dir);
            if(along<0||along>reach||along>=closest)continue;
            if(delta.subtract(dir.scale(along)).length()<=Math.max(.85,entity.getBbWidth()*.75)){
                HitResult wall=level.clip(new net.minecraft.world.level.ClipContext(origin,
                    origin.add(dir.scale(along)),net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,player));
                if(wall.getType()==HitResult.Type.MISS){closest=along;target=entity;}
            }
        }
        return target;
    }

    private static float resonance(ServerPlayer player,Glyph element){
        ServerLevel level=(ServerLevel)player.level();
        return switch(element){
            case CINDER,LIGHT -> level.getDayTime()%24000<12000&&level.canSeeSky(player.blockPosition())?1.28f:1f;
            case BLOOM,ROOT,TIDE -> level.isRainingAt(player.blockPosition())?1.35f:1f;
            case RIME,STORM -> player.isInWater()||level.isRainingAt(player.blockPosition())?1.36f:1f;
            case VEIL,SHADOW,WARP -> level.getDayTime()%24000>=13000?1.35f:1f;
            case STONE,METAL -> player.blockPosition().getY()<50?1.24f:1f;
            case WIND -> level.canSeeSky(player.blockPosition())?1.20f:1f;
            default -> 1f;
        };
    }
    private static ParticleOptions visual(Glyph element){
        return switch(element){
            case CINDER -> ParticleTypes.FLAME;
            case BLOOM,ROOT -> ParticleTypes.HAPPY_VILLAGER;
            case RIME -> ParticleTypes.SNOWFLAKE;
            case VEIL,WARP -> ParticleTypes.PORTAL;
            case STORM -> ParticleTypes.ELECTRIC_SPARK;
            case LIGHT -> ParticleTypes.END_ROD;
            case SHADOW -> ParticleTypes.SOUL;
            case WIND -> ParticleTypes.CLOUD;
            case STONE,METAL -> ParticleTypes.ENCHANT;
            case TIDE -> ParticleTypes.BUBBLE;
            default -> ParticleTypes.ENCHANT;
        };
    }
    private static void beam(ServerLevel level,Vec3 from,Vec3 to,Glyph element){
        Vec3 delta=to.subtract(from);
        int n=Math.min(95,Math.max(15,(int)(delta.length()*5)));
        for(int i=0;i<=n;i++){
            double t=i/(double)n;
            Vec3 p=from.add(delta.scale(t));
            level.sendParticles(visual(element),p.x,p.y,p.z,1,0,0,0,.012);
            if(i%4==0){
                level.sendParticles(ParticleTypes.END_ROD,p.x+.12*Math.cos(t*42),
                    p.y+.12*Math.sin(t*42),p.z+.12*Math.sin(t*17),1,0,0,0,0);
            }
        }
    }
    private static void circle(ServerLevel level,Vec3 at,Glyph element,double radius,int count){
        for(int i=0;i<count;i++){
            double a=2*Math.PI*i/count;
            double x=at.x+radius*Math.cos(a),z=at.z+radius*Math.sin(a);
            level.sendParticles(visual(element),x,at.y+.24,z,1,0,0,0,0);
        }
    }
    private static void burst(ServerLevel level,Vec3 at,Glyph element,int layers,double radius){
        for(int tier=0;tier<layers;tier++){
            double r=radius*(.55+.36*tier);
            for(int i=0;i<24;i++){
                double a=(Math.PI*2*i/24)+(tier*.31);
                double x=at.x+r*Math.cos(a),y=at.y+.2+tier*.5,z=at.z+r*Math.sin(a);
                level.sendParticles(visual(element),x,y,z,1,0,0,0,.018);
                if(i%4==0)level.sendParticles(ParticleTypes.END_ROD,x,y+.2,z,1,0,0,0,.01);
            }
        }
        level.sendParticles(visual(element),at.x,at.y,at.z,42,radius*.4,radius*.4,radius*.4,.09);
    }
    private static void say(ServerPlayer player,String msg){
        player.displayClientMessage(Component.literal(msg),true);
    }
    public static void tick(MinecraftServer server){
        tick++;
        for(ServerPlayer player:server.getPlayerList().getPlayers()){
            INK.put(player.getUUID(),Math.min(100,mana(player)+.20f));
            if(tick%20==0) ServerPlayNetworking.send(player,new ManaPayload(Math.round(mana(player))));
        }
        for(Iterator<Pending> it=PENDING.iterator();it.hasNext();){
            Pending pending=it.next();
            if(pending.due()<=tick){
                it.remove();
                ServerPlayer caster=server.getPlayerList().getPlayer(pending.owner());
                if(caster!=null)apply(caster,pending.intent(),pending.element(),null,pending.power());
            }
        }
        for(Iterator<Seal> it=SEALS.iterator();it.hasNext();){
            Seal seal=it.next();
            if(tick>seal.expiry){it.remove();continue;}
            if(tick%10==0)circle(seal.level,seal.pos,seal.element,1.4,20);
            if(tick<seal.armed||tick%5!=0)continue;
            ServerPlayer owner=server.getPlayerList().getPlayer(seal.owner);
            if(owner==null||owner.level()!=seal.level){it.remove();continue;}
            List<LivingEntity> entities=seal.level.getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(seal.pos,3.5,3.5,3.5),
                e->e.isAlive()&&e!=owner&&e.distanceToSqr(seal.pos)<4);
            if(!entities.isEmpty()){
                for(LivingEntity e:entities)
                    affect(seal.level,e,seal.element,seal.modifier==Glyph.INVERT,seal.power);
                WorldMagic.rewrite(seal.level,BlockPos.containing(seal.pos),seal.element,
                    seal.modifier==Glyph.INVERT,1,seal.power,owner);
                burst(seal.level,seal.pos,seal.element,3,1.6);
                it.remove();
            }
        }
    }
}
