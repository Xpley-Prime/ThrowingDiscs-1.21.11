package dev.palimpsest.magic;

import dev.palimpsest.glyph.Glyph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative environmental grammar. No fake visual-only "world spells".
 * Strict 5x5/7x7 bounded areas; only explicitly supported replaceable or
 * transformable blocks may change. No block-entity replacement.
 */
public final class WorldMagic {
    private WorldMagic() {}

    public static int rewrite(ServerLevel level,BlockPos center,Glyph element,boolean invert,
                              int radius,float power,ServerPlayer caster) {
        radius=Math.clamp(radius,0,3);
        int edited=0;
        for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++) {
            if(dx*dx+dz*dz > radius*radius+.5 && radius>0)continue;
            BlockPos at=center.offset(dx,0,dz);
            if(!level.isLoaded(at)||!level.getWorldBorder().isWithinBounds(at))continue;
            if(change(level,at,element,invert,dx==0&&dz==0)) edited++;
            if(element==Glyph.BLOOM || element==Glyph.ROOT) {
                BlockPos under=at.below();
                if(level.isLoaded(under) && change(level,under,element,invert,false)) edited++;
            }
        }
        if(element==Glyph.STORM && !invert && level.canSeeSky(center) && level.isLoaded(center)) {
            var bolt=EntityType.LIGHTNING_BOLT.create(level,EntitySpawnReason.TRIGGERED);
            if(bolt!=null) {
                bolt.setPos(center.getX()+.5,center.getY()+.1,center.getZ()+.5);
                level.addFreshEntity(bolt);
                edited++;
            }
        }
        if(element==Glyph.WIND) {
            for(ItemEntity item:level.getEntitiesOfClass(ItemEntity.class,new AABB(center).inflate(4))) {
                Vec3 away=item.position().subtract(Vec3.atCenterOf(center));
                Vec3 velocity=away.lengthSqr()<.1?new Vec3(0,.5,0):away.normalize().scale(invert?-.65:.65);
                item.setDeltaMovement(item.getDeltaMovement().add(velocity.x,.25,velocity.z));
                edited++;
            }
        }
        if(edited>0) {
            level.sendParticles(ParticleTypes.END_ROD,center.getX()+.5,center.getY()+.5,
                center.getZ()+.5,Math.min(65,10+edited*3),.7,.5,.7,.10);
        }
        return edited;
    }

    private static boolean change(ServerLevel level,BlockPos at,Glyph kind,boolean invert,boolean center) {
        BlockState state=level.getBlockState(at);
        if(kind==Glyph.BLOOM || kind==Glyph.ROOT) {
            if(!invert && state.getBlock() instanceof BonemealableBlock grow &&
                grow.isValidBonemealTarget(level,at,state)) {
                if(grow.isBonemealSuccess(level,level.random,at,state)) {
                    grow.performBonemeal(level,level.random,at,state);
                    return true;
                }
            }
        }
        if(kind==Glyph.CINDER) {
            if(invert) {
                if(state.is(Blocks.FIRE)||state.is(Blocks.SOUL_FIRE))
                    return level.setBlock(at,Blocks.AIR.defaultBlockState(),3);
                if(state.is(Blocks.MAGMA_BLOCK))return level.setBlock(at,Blocks.COBBLESTONE.defaultBlockState(),3);
            } else {
                if(state.is(Blocks.SAND))return level.setBlock(at,Blocks.GLASS.defaultBlockState(),3);
                if(center && state.canBeReplaced() && level.getBlockState(at.below()).isSolid())
                    return level.setBlock(at,Blocks.FIRE.defaultBlockState(),3);
            }
        }
        if(kind==Glyph.BLOOM) {
            if(invert && state.is(Blocks.GRASS_BLOCK))
                return level.setBlock(at,Blocks.DIRT.defaultBlockState(),3);
            if(!invert && state.is(Blocks.DIRT))
                return level.setBlock(at,Blocks.GRASS_BLOCK.defaultBlockState(),3);
        }
        if(kind==Glyph.RIME) {
            if(state.is(Blocks.WATER)&&!invert)return level.setBlock(at,Blocks.ICE.defaultBlockState(),3);
            if(invert && state.is(Blocks.ICE))return level.setBlock(at,Blocks.WATER.defaultBlockState(),3);
            if(!invert && state.is(Blocks.LAVA)&&center)return level.setBlock(at,Blocks.OBSIDIAN.defaultBlockState(),3);
        }
        if(kind==Glyph.VEIL) {
            if(invert && state.is(Blocks.COBWEB))return level.setBlock(at,Blocks.AIR.defaultBlockState(),3);
            if(!invert && center && state.canBeReplaced() &&
                level.getBlockState(at.below()).isSolid())
                return level.setBlock(at,Blocks.COBWEB.defaultBlockState(),3);
        }
        if(kind==Glyph.STONE) {
            if(invert && state.is(Blocks.STONE))return level.setBlock(at,Blocks.GRAVEL.defaultBlockState(),3);
            if(state.is(Blocks.GRAVEL))return level.setBlock(at,Blocks.STONE.defaultBlockState(),3);
            if(state.is(Blocks.SAND))return level.setBlock(at,Blocks.SANDSTONE.defaultBlockState(),3);
            if(!invert && center && state.canBeReplaced())
                return level.setBlock(at,Blocks.COBBLESTONE.defaultBlockState(),3);
        }
        if(kind==Glyph.TIDE) {
            if(invert && state.is(Blocks.WATER))
                return level.setBlock(at,Blocks.AIR.defaultBlockState(),3);
            if(!invert && state.canBeReplaced() && (center || level.getBlockState(at.below()).isSolid()))
                return level.setBlock(at,Blocks.WATER.defaultBlockState(),3);
        }
        if(kind==Glyph.STORM) {
            if(state.is(Blocks.COPPER_BLOCK)&&!invert)
                return level.setBlock(at,Blocks.WEATHERED_COPPER.defaultBlockState(),3);
            if(state.is(Blocks.WEATHERED_COPPER)&&invert)
                return level.setBlock(at,Blocks.COPPER_BLOCK.defaultBlockState(),3);
        }
        if(kind==Glyph.LIGHT) {
            if(invert && state.is(Blocks.GLOWSTONE))return level.setBlock(at,Blocks.AIR.defaultBlockState(),3);
            if(!invert && center && state.canBeReplaced())
                return level.setBlock(at,Blocks.GLOWSTONE.defaultBlockState(),3);
        }
        if(kind==Glyph.SHADOW) {
            if(!invert && state.is(Blocks.GRASS_BLOCK))
                return level.setBlock(at,Blocks.MYCELIUM.defaultBlockState(),3);
            if(invert && state.is(Blocks.MYCELIUM))
                return level.setBlock(at,Blocks.GRASS_BLOCK.defaultBlockState(),3);
        }
        if(kind==Glyph.WARP) {
            if(!invert && state.is(Blocks.STONE))
                return level.setBlock(at,Blocks.END_STONE.defaultBlockState(),3);
            if(invert && state.is(Blocks.END_STONE))
                return level.setBlock(at,Blocks.STONE.defaultBlockState(),3);
        }
        if(kind==Glyph.ROOT) {
            if(!invert && state.is(Blocks.DIRT))
                return level.setBlock(at,Blocks.MOSS_BLOCK.defaultBlockState(),3);
            if(invert && state.is(Blocks.MOSS_BLOCK))
                return level.setBlock(at,Blocks.DIRT.defaultBlockState(),3);
        }
        if(kind==Glyph.METAL) {
            if(!invert && state.is(Blocks.RAW_COPPER_BLOCK))
                return level.setBlock(at,Blocks.COPPER_BLOCK.defaultBlockState(),3);
            if(invert && state.is(Blocks.COPPER_BLOCK))
                return level.setBlock(at,Blocks.RAW_COPPER_BLOCK.defaultBlockState(),3);
            if(!invert && center && state.canBeReplaced())
                return level.setBlock(at,Blocks.IRON_BARS.defaultBlockState(),3);
        }
        if(kind==Glyph.WIND) {
            if(state.is(Blocks.SNOW)||state.is(Blocks.FIRE))
                return level.setBlock(at,Blocks.AIR.defaultBlockState(),3);
            if(invert && center && state.canBeReplaced()&&level.getBlockState(at.below()).isSolid())
                return level.setBlock(at,Blocks.SNOW.defaultBlockState(),3);
        }
        return false;
    }
}
