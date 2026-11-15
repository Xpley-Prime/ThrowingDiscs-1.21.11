package dev.palimpsest.content;

import dev.palimpsest.magic.MagicEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Forty material rituals, summoned at a physical altar using consumed catalysts. */
public final class ArcaneRituals {
    public enum Ritual {
        RED_PULSE("Красный импульс",Items.REDSTONE,"Устанавливает редстоун-блок для питания машин"),
        LUMEN_SEED("Семя света",Items.GLOWSTONE_DUST,"Выращивает светящийся кристалл"),
        WHITE_PILLAR("Белый столп",Items.QUARTZ,"Создаёт кварцевую колонну"),
        FAMILIAR("Зверь-спутник",Items.BONE,"Призывает волка"),
        HERO_OATH("Клятва героя",Items.EMERALD,"Дарует доверие деревень"),
        ALCHEMY_OF_AGE("Вековая плата",Items.GOLD_INGOT,"Меняет золото на опыт"),
        COPPER_AGE("Столетие меди",Items.COPPER_INGOT,"Состаривает медь"),
        IRON_CIRCLE("Железный круг",Items.IRON_INGOT,"Возводит железное ограждение"),
        ANIMAL_FEAST("Звериный пир",Items.WHEAT,"Лечит животных поблизости"),
        APPLE_GROVE("Яблоневый сад",Items.APPLE,"Сажает молодой дуб"),
        FIELD_SONG("Полевая песня",Items.BEETROOT_SEEDS,"Засевает грядки свёклой"),
        VENOM_CLOUD("Облако яда",Items.SPIDER_EYE,"Отравляет враждебных существ"),
        FLESH_DECAY("Гниение плоти",Items.ROTTEN_FLESH,"Ослабляет чудовищ"),
        SHOCK_WAVE("Звёздный удар",Items.GUNPOWDER,"Создаёт магический взрыв без разрушения блоков"),
        SLIME_LIFT("Слизневый подъём",Items.SLIME_BALL,"Подбрасывает ближайших существ"),
        COPPER_SEAL("Печать защиты",Items.HONEYCOMB,"Покрывает медь воском"),
        NETHER_BLOOM("Незерский всход",Items.NETHER_WART,"Проращивает адский нарост"),
        EYES_OF_INK("Сияющие глаза",Items.GLOW_INK_SAC,"Отмечает близких существ свечением"),
        SNOW_MEADOW("Снежный ковёр",Items.SNOWBALL,"Осыпает землю снегом"),
        THAW("Оттепель",Items.MAGMA_CREAM,"Растапливает снег и лёд"),
        KELP_FOREST("Лес водорослей",Items.KELP,"Сажает водоросли в воде"),
        OCEAN_STONE("Океанский камень",Items.PRISMARINE_SHARD,"Создаёт призмариновую глыбу"),
        REEF_REBIRTH("Возрождение рифа",Items.TUBE_CORAL,"Возвращает живые кораллы"),
        NIGHT_MIST("Ночная мгла",Items.FERMENTED_SPIDER_EYE,"Лишает врагов зрения"),
        BANISH("Изгнание",Items.ENDER_PEARL,"Отправляет ближайшего врага прочь"),
        CHORUS_DANCE("Хоровой танец",Items.CHORUS_FRUIT,"Перемещает нескольких союзников"),
        FLOATING_VOW("Клятва невесомости",Items.PHANTOM_MEMBRANE,"Даёт союзникам плавное падение"),
        TEAR_OF_LIFE("Слеза жизни",Items.GHAST_TEAR,"Заживляет раны всех союзников"),
        REMEMBRANCE("Воспоминание",Items.ECHO_SHARD,"Повторяет последний ритуал"),
        FORTUNE("Обряд удачи",Items.RABBIT_FOOT,"Дарует особую удачу"),
        SHELL_CIRCLE("Панцирный круг",Items.TURTLE_SCUTE,"Окутывает союзников защитой"),
        FIRE_SWARM("Пламенный рой",Items.BLAZE_POWDER,"Поджигает вражеских существ"),
        CRYSTAL_GARDEN("Хрустальный сад",Items.AMETHYST_SHARD,"Выращивает аметистовый бутон"),
        CAMP_HEARTH("Походный очаг",Items.CHARCOAL,"Разжигает походный костёр"),
        ASCENSION("Лестница",Items.STICK,"Создаёт опору для подъёма"),
        GUST("Порыв",Items.FEATHER,"Поднимает животных и врагов"),
        NEW_LIFE("Новая жизнь",Items.EGG,"Призывает курицу"),
        SONG_OF_SEA("Песня моря",Items.NAUTILUS_SHELL,"Призывает морского друга"),
        SCRIBE_OATH("Клятва писца",Items.PAPER,"Создаёт книгу с пером"),
        INK_ECLIPSE("Чернильное затмение",Items.INK_SAC,"Погружает врагов во тьму");
        public final String name,description;
        public final Item catalyst;
        Ritual(String name,Item catalyst,String description){this.name=name;this.catalyst=catalyst;this.description=description;}
    }
    private static final Map<UUID,Ritual> LAST=new HashMap<>();
    public static java.util.Map<UUID,Ritual> snapshotHistory(){return java.util.Map.copyOf(LAST);}
    public static void loadHistory(java.util.Map<UUID,Ritual> data){LAST.clear();LAST.putAll(data);}
    private ArcaneRituals() {}

    public static String describe(int start,int count) {
        StringBuilder out=new StringBuilder();
        Ritual[] rituals=Ritual.values();
        for(int i=start;i<Math.min(rituals.length,start+count);i++)
            out.append(i+1).append(". ").append(rituals[i].name).append(" — ")
                .append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(rituals[i].catalyst).toString()).append("\n");
        return out.toString().stripTrailing();
    }

    public static boolean perform(ServerPlayer player,BlockPos altar) {
        ItemStack item=player.getMainHandItem();
        Ritual chosen=null;
        for(Ritual r:Ritual.values()) if(item.is(r.catalyst)) {chosen=r;break;}
        if(chosen==null) {
            player.displayClientMessage(Component.literal("§6Положи катализатор в руку. /palimpsest rituals"),true);
            return false;
        }
        int cost=chosen==Ritual.REMEMBRANCE?21:15;
        if(MagicEngine.mana(player)<cost && !player.getAbilities().instabuild){
            player.displayClientMessage(Component.literal("§cРитуалу нужно "+cost+" чернил."),true);return false;
        }
        Ritual actual=chosen;
        if(chosen==Ritual.REMEMBRANCE){
            actual=LAST.get(player.getUUID());
            if(actual==null || actual==Ritual.REMEMBRANCE){
                player.displayClientMessage(Component.literal("§7Эхо не помнит прошлого обряда."),true);return false;
            }
        }
        if(!enact(player,altar,actual)){
            player.displayClientMessage(Component.literal("§cНет подходящей области или существа для обряда."),true);return false;
        }
        if(!player.getAbilities().instabuild)item.shrink(1);
        MagicEngine.spend(player,cost);
        LAST.put(player.getUUID(),actual);
        ServerLevel level=(ServerLevel)player.level();
        level.sendParticles(ParticleTypes.ENCHANT,altar.getX()+.5,altar.getY()+1.15,altar.getZ()+.5,100,1.1,.7,1.1,.08);
        player.sendSystemMessage(Component.literal("§d✦ "+actual.name+" §7— "+actual.description));
        return true;
    }
    private static boolean place(ServerLevel level,BlockPos pos,Block block) {
        if(!level.isLoaded(pos)||!level.getWorldBorder().isWithinBounds(pos))return false;
        if(!level.getBlockState(pos).canBeReplaced())return false;
        return level.setBlock(pos,block.defaultBlockState(),3);
    }
    private static boolean convert(ServerLevel level,BlockPos pos,Block from,Block to) {
        if(!level.getBlockState(pos).is(from))return false;
        return level.setBlock(pos,to.defaultBlockState(),3);
    }
    private static boolean around(ServerLevel level,BlockPos origin,Block from,Block to,int radius) {
        boolean found=false;
        for(BlockPos p:BlockPos.betweenClosed(origin.offset(-radius,-1,-radius),origin.offset(radius,1,radius))) {
            if(convert(level,p.immutable(),from,to))found=true;
        }
        return found;
    }
    private static java.util.List<LivingEntity> nearby(ServerLevel level,BlockPos pos) {
        return level.getEntitiesOfClass(LivingEntity.class,new AABB(pos).inflate(9),
            entity->entity.isAlive());
    }
    private static boolean spawn(ServerLevel level,BlockPos pos,EntityType<?> type) {
        var entity=type.create(level,EntitySpawnReason.MOB_SUMMONED);
        if(entity==null)return false;
        entity.setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5);
        return level.addFreshEntity(entity);
    }
    private static boolean enact(ServerPlayer player,BlockPos pos,Ritual r) {
        ServerLevel level=(ServerLevel)player.level();
        BlockPos above=pos.above(),north=pos.north(),east=pos.east();
        boolean changed=true;
        switch(r) {
            case RED_PULSE -> changed=place(level,above,Blocks.REDSTONE_BLOCK);
            case LUMEN_SEED -> changed=place(level,above,Blocks.GLOWSTONE);
            case WHITE_PILLAR -> changed=place(level,above,Blocks.QUARTZ_PILLAR);
            case FAMILIAR -> changed=spawn(level,east,EntityType.WOLF);
            case HERO_OATH -> player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE,20*150,0));
            case ALCHEMY_OF_AGE -> player.giveExperiencePoints(140);
            case COPPER_AGE -> changed=around(level,pos,Blocks.COPPER_BLOCK,Blocks.EXPOSED_COPPER,3);
            case IRON_CIRCLE -> {
                changed=false;
                for(var p:new BlockPos[]{north,east,pos.south(),pos.west()})
                    if(place(level,p.above(),Blocks.IRON_BARS))changed=true;
            }
            case ANIMAL_FEAST -> {
                for(LivingEntity e:nearby(level,pos)) if(!(e instanceof Player)&&!(e instanceof Monster))e.heal(7);
            }
            case APPLE_GROVE -> changed=place(level,east,Blocks.OAK_SAPLING);
            case FIELD_SONG -> {
                changed=false;
                for(BlockPos p:BlockPos.betweenClosed(pos.offset(-4,-1,-4),pos.offset(4,0,4)))
                    if(level.getBlockState(p).is(Blocks.FARMLAND) && place(level,p.above(),Blocks.BEETROOTS))changed=true;
            }
            case VENOM_CLOUD -> {
                for(LivingEntity e:nearby(level,pos)) if(e instanceof Monster)
                    e.addEffect(new MobEffectInstance(MobEffects.POISON,150,1));
            }
            case FLESH_DECAY -> {
                for(LivingEntity e:nearby(level,pos)) if(e instanceof Monster)
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,240,1));
            }
            case SHOCK_WAVE -> {
                for(LivingEntity e:nearby(level,pos)) if(e instanceof Monster)
                    e.hurtServer(level,level.damageSources().magic(),8);
            }
            case SLIME_LIFT -> {
                for(LivingEntity e:nearby(level,pos)) if(e!=player)
                    e.setDeltaMovement(e.getDeltaMovement().add(0,1.5,0));
            }
            case COPPER_SEAL -> changed=around(level,pos,Blocks.COPPER_BLOCK,Blocks.WAXED_COPPER_BLOCK,3);
            case NETHER_BLOOM -> {
                changed=false;
                for(BlockPos p:BlockPos.betweenClosed(pos.offset(-3,-2,-3),pos.offset(3,0,3)))
                    if(level.getBlockState(p).is(Blocks.SOUL_SAND) && place(level,p.above(),Blocks.NETHER_WART))changed=true;
            }
            case EYES_OF_INK -> {
                for(LivingEntity e:nearby(level,pos))
                    e.addEffect(new MobEffectInstance(MobEffects.GLOWING,400,0));
            }
            case SNOW_MEADOW -> {
                changed=false;
                for(BlockPos p:BlockPos.betweenClosed(pos.offset(-3,-1,-3),pos.offset(3,0,3))) {
                    if(level.getBlockState(p).isSolid() && place(level,p.above(),Blocks.SNOW))changed=true;
                }
            }
            case THAW -> {
                changed=around(level,pos,Blocks.SNOW,Blocks.AIR,4);
                changed=around(level,pos,Blocks.ICE,Blocks.WATER,4)||changed;
            }
            case KELP_FOREST -> {
                changed=false;
                for(BlockPos p:BlockPos.betweenClosed(pos.offset(-3,-2,-3),pos.offset(3,1,3))) {
                    if(level.getBlockState(p).is(Blocks.WATER)) {
                        if(level.setBlock(p.immutable(),Blocks.KELP.defaultBlockState(),3))changed=true;
                    }
                }
            }
            case OCEAN_STONE -> changed=place(level,east,Blocks.PRISMARINE);
            case REEF_REBIRTH -> {
                changed=around(level,pos,Blocks.DEAD_TUBE_CORAL_BLOCK,Blocks.TUBE_CORAL_BLOCK,3);
            }
            case NIGHT_MIST -> {
                for(LivingEntity e:nearby(level,pos)) if(e instanceof Monster)
                    e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,180,0));
            }
            case BANISH -> {
                changed=false;
                for(LivingEntity e:nearby(level,pos)) if(e instanceof Monster) {
                    Vec3 direction=e.position().subtract(Vec3.atCenterOf(pos)).normalize().scale(11);
                    Vec3 target=e.position().add(direction);
                    BlockPos p=BlockPos.containing(target);
                    if(level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()) {
                        e.teleportTo(target.x,target.y,target.z);changed=true;break;
                    }
                }
            }
            case CHORUS_DANCE -> {
                for(ServerPlayer p:level.players())if(p.distanceToSqr(Vec3.atCenterOf(pos))<90){
                    Vec3 target=p.position().add(level.random.nextInt(9)-4,1,level.random.nextInt(9)-4);
                    BlockPos dest=BlockPos.containing(target);
                    if(level.getBlockState(dest).isAir()&&level.getBlockState(dest.above()).isAir())
                        p.teleportTo(target.x,target.y,target.z);
                }
            }
            case FLOATING_VOW -> {
                for(ServerPlayer p:level.players())if(p.distanceToSqr(Vec3.atCenterOf(pos))<144)
                    p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,600,0));
            }
            case TEAR_OF_LIFE -> {
                for(LivingEntity e:nearby(level,pos))if(!(e instanceof Monster))e.heal(10);
            }
            case REMEMBRANCE -> changed=false;
            case FORTUNE -> player.addEffect(new MobEffectInstance(MobEffects.LUCK,20*180,0));
            case SHELL_CIRCLE -> {
                for(ServerPlayer p:level.players())if(p.distanceToSqr(Vec3.atCenterOf(pos))<144)
                    p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,20*60,1));
            }
            case FIRE_SWARM -> {
                for(LivingEntity e:nearby(level,pos))if(e instanceof Monster)e.igniteForSeconds(8);
            }
            case CRYSTAL_GARDEN -> changed=place(level,above,Blocks.AMETHYST_CLUSTER);
            case CAMP_HEARTH -> changed=place(level,above,Blocks.CAMPFIRE);
            case ASCENSION -> changed=place(level,above,Blocks.SCAFFOLDING);
            case GUST -> {
                for(LivingEntity e:nearby(level,pos))if(e!=player)
                    e.addEffect(new MobEffectInstance(MobEffects.LEVITATION,60,0));
            }
            case NEW_LIFE -> changed=spawn(level,east,EntityType.CHICKEN);
            case SONG_OF_SEA -> {
                changed=false;
                for(BlockPos p:BlockPos.betweenClosed(pos.offset(-5,-2,-5),pos.offset(5,0,5)))
                    if(level.getBlockState(p).is(Blocks.WATER)) {changed=spawn(level,p.immutable(),EntityType.DOLPHIN);break;}
            }
            case SCRIBE_OATH -> {
                ItemStack reward=new ItemStack(Items.WRITABLE_BOOK);
                if(!player.getInventory().add(reward))player.drop(reward,false);
            }
            case INK_ECLIPSE -> {
                for(LivingEntity e:nearby(level,pos))if(e instanceof Monster)
                    e.addEffect(new MobEffectInstance(MobEffects.DARKNESS,180,0));
            }
        }
        return changed;
    }
}
