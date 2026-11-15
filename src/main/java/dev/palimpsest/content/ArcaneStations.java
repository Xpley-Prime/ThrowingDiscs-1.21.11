package dev.palimpsest.content;

import dev.palimpsest.Palimpsest;
import dev.palimpsest.magic.MagicEngine;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** 28 physically placeable magical workstations, each with a different on-use mechanic. */
public final class ArcaneStations {
    public enum Station {
        INK_WELL("ink_well","Чернильный колодец","Наполняет чернильный запас"),
        RITUAL_ALTAR("ritual_altar","Алтарь переплёта","Сотворяет материальные ритуалы"),
        SCRIBE_DESK("scribe_desk","Письменный стол","Показывает забытые символы"),
        VERDANT_TROUGH("verdant_trough","Ложе растений","Ускоряет урожай рядом"),
        SOLAR_KILN("solar_kiln","Солнечный горн","Переплавляет медную руду"),
        PURIFICATION_FONT("purification_font","Купель очищения","Убирает эффекты состояния"),
        STAR_CHART("star_chart","Звёздная карта","Определяет положение в мире"),
        HEART_FOUNTAIN("heart_fountain","Фонтан сердца","Восстанавливает здоровье и пищу"),
        GUARDIAN_PLINTH("guardian_plinth","Постамент стража","Поражает ближайших монстров"),
        LUMEN_BRAZIER("lumen_brazier","Брачный огонь","Дарует ночное зрение окружающим"),
        MIRROR_SLAB("mirror_slab","Зеркальная плита","Скрывает мага и сбрасывает левитацию"),
        ORE_RESONATOR("ore_resonator","Резонатор руды","Находит ближайшую жилу"),
        MENDING_FORGE("mending_forge","Горн починки","Восстанавливает прочность вещей"),
        BASALT_TRANSMUTER("basalt_transmuter","Каменный трансмутатор","Выплавляет камень из булыжника"),
        TIDAL_FOUNTAIN("tidal_fountain","Источник прилива","Создаёт подводное убежище"),
        SEED_VAULT("seed_vault","Семенной архив","Превращает семена в костную муку"),
        HOME_OBELISK("home_obelisk","Домашний обелиск","Запоминает точку возвращения"),
        REST_WAYSTONE("rest_waystone","Камень отдыха","Снимает усталость и насыщает"),
        CHARM_PRESS("charm_press","Пресс чар","Делает сияющую магическую пыль"),
        RUNE_CARVER("rune_carver","Резчик символов","Разлагает камень на кварц"),
        HOARFROST_ALTAR("hoarfrost_altar","Инейный алтарь","Замораживает пруды рядом"),
        LAVA_CRUCIBLE("lava_crucible","Лавовый тигель","Затвердевает лаву вокруг"),
        AEOLIAN_FAN("aeolian_fan","Эолова ветряница","Разгоняет упавшие вещи"),
        SUNDIAL_TOWER("sundial_tower","Башня времени","Сдвигает время мира"),
        BEAST_BELL("beast_bell","Звериный колокол","Лечит близких существ"),
        RELIC_FORGE("relic_forge","Кузня реликвий","Преображает кристаллы в ядра"),
        GOLD_REFINER("gold_refiner","Золотой ректификатор","Перерабатывает железо в золото"),
        SOUL_RELIQUARY("soul_reliquary","Реликварий душ","Меняет опыт на чернила");
        public final String id,title,description;
        Station(String id,String title,String description) {this.id=id;this.title=title;this.description=description;}
    }
    private static final EnumMap<Station,Block> STATIONS = new EnumMap<>(Station.class);
    private static final Map<Block,Station> BY_BLOCK=new HashMap<>();
    private static final Map<String,Long> LAST_USE=new HashMap<>();
    private ArcaneStations() {}

    public static void register() {
        for(Station station:Station.values()) {
            ResourceKey<Block> key=ResourceKey.create(Registries.BLOCK,Palimpsest.id(station.id));
            Block block=Registry.register(BuiltInRegistries.BLOCK,key,
                new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_BLOCK).setId(key)));
            ResourceKey<Item> itemKey=ResourceKey.create(Registries.ITEM,Palimpsest.id(station.id));
            Registry.register(BuiltInRegistries.ITEM,itemKey,
                new BlockItem(block,new Item.Properties().setId(itemKey)));
            STATIONS.put(station,block);BY_BLOCK.put(block,station);
        }
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries->
            STATIONS.values().forEach(block->entries.accept(block.asItem())));
        UseBlockCallback.EVENT.register((player,level,hand,hit)->{
            Station station=BY_BLOCK.get(level.getBlockState(hit.getBlockPos()).getBlock());
            if(station==null)return InteractionResult.PASS;
            if(player instanceof ServerPlayer server) activate(server,hit.getBlockPos(),station);
            return InteractionResult.SUCCESS;
        });
    }

    private static void inform(ServerPlayer player,String text) {
        player.displayClientMessage(Component.literal("§d"+text),true);
    }
    private static boolean exchange(ServerPlayer player,Item from,int quantity,Item to,int output) {
        ItemStack stack=player.getMainHandItem();
        if(!stack.is(from)||stack.getCount()<quantity)return false;
        if(!player.getAbilities().instabuild)stack.shrink(quantity);
        ItemStack reward=new ItemStack(to,output);
        if(!player.getInventory().add(reward))player.drop(reward,false);
        return true;
    }
    private static boolean spread(ServerLevel level,BlockPos origin,Block before,Block after,int radius) {
        boolean changed=false;
        for(BlockPos mutable:BlockPos.betweenClosed(origin.offset(-radius,-1,-radius),origin.offset(radius,1,radius))) {
            BlockPos pos=mutable.immutable();
            if(level.getBlockState(pos).is(before)) {
                level.setBlock(pos,after.defaultBlockState(),3);
                changed=true;
            }
        }
        return changed;
    }
    private static void activate(ServerPlayer player,BlockPos pos,Station station) {
        ServerLevel level=(ServerLevel)player.level();
        String key=level.dimension().identifier()+":"+pos.asLong();
        long now=level.getGameTime();
        if(LAST_USE.getOrDefault(key,-10000L)>now) {inform(player,"Устройство перезаряжается.");return;}
        int cost=switch(station) {case INK_WELL,RITUAL_ALTAR,SOUL_RELIQUARY->0;default->12;};
        if(MagicEngine.mana(player)<cost && !player.getAbilities().instabuild) {
            inform(player,"Чернил недостаточно: нужно "+cost);return;
        }
        boolean ok=true;
        switch(station) {
            case INK_WELL -> {
                if(exchange(player,Items.INK_SAC,1,Items.GLASS_BOTTLE,1)) MagicEngine.refill(player,65);
                else {inform(player,"Вставь чернильный мешок.");ok=false;}
            }
            case RITUAL_ALTAR -> {ok=ArcaneRituals.perform(player,pos);}
            case SCRIBE_DESK -> {
                inform(player,"Письмо: намерение → стихия → необязательный оттенок. /palimpsest guide");
            }
            case VERDANT_TROUGH -> {
                ok=false;
                for(BlockPos mutable:BlockPos.betweenClosed(pos.offset(-3,-1,-3),pos.offset(3,1,3))) {
                    BlockPos p=mutable.immutable();
                    BlockState state=level.getBlockState(p);
                    if(state.getBlock() instanceof BonemealableBlock growing &&
                        growing.isValidBonemealTarget(level,p,state)) {
                        growing.performBonemeal(level,level.random,p,state);ok=true;
                    }
                }
            }
            case SOLAR_KILN -> {ok=exchange(player,Items.RAW_COPPER,1,Items.COPPER_INGOT,1);}
            case PURIFICATION_FONT -> player.removeAllEffects();
            case STAR_CHART -> {
                inform(player,"Мир "+level.dimension().identifier()+", XYZ "+pos.toShortString()+
                    ", время "+(level.getDayTime()%24000)+" / 24000");
            }
            case HEART_FOUNTAIN -> {
                player.heal(12);
                player.getFoodData().eat(6,1f);
            }
            case GUARDIAN_PLINTH -> {
                for(Monster mob:level.getEntitiesOfClass(Monster.class,
                    new net.minecraft.world.phys.AABB(pos).inflate(8))) {
                    mob.hurtServer(level,level.damageSources().magic(),7);
                }
            }
            case LUMEN_BRAZIER -> {
                for(ServerPlayer p:level.players())
                    if(p.distanceToSqr(Vec3.atCenterOf(pos))<144)
                        p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,20*90,0));
            }
            case MIRROR_SLAB -> {
                player.removeEffect(MobEffects.LEVITATION);
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,20*40,0));
            }
            case ORE_RESONATOR -> {
                double best=Double.MAX_VALUE;BlockPos where=null;
                for(BlockPos mutable:BlockPos.betweenClosed(pos.offset(-10,-10,-10),pos.offset(10,10,10))){
                    BlockPos p=mutable.immutable();
                    String id=BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).toString();
                    if(id.endsWith("_ore")||id.endsWith("ancient_debris")) {
                        double d=p.distSqr(pos);if(d<best){best=d;where=p;}
                    }
                }
                if(where==null){inform(player,"Руды рядом не обнаружено.");ok=false;}
                else inform(player,"Жила поблизости: "+where.toShortString());
            }
            case MENDING_FORGE -> {
                ItemStack stack=player.getMainHandItem();
                if(!stack.isDamaged())ok=false;
                else stack.setDamageValue(Math.max(0,stack.getDamageValue()-80));
            }
            case BASALT_TRANSMUTER -> {ok=exchange(player,Items.COBBLESTONE,8,Items.STONE,8);}
            case TIDAL_FOUNTAIN -> {
                for(ServerPlayer p:level.players())
                    if(p.distanceToSqr(Vec3.atCenterOf(pos))<100)
                        p.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING,20*120,0));
            }
            case SEED_VAULT -> {ok=exchange(player,Items.WHEAT_SEEDS,4,Items.BONE_MEAL,1);}
            case HOME_OBELISK -> ArcaneTools.bindAnchor(player,pos.above());
            case REST_WAYSTONE -> {
                player.getFoodData().eat(12,1f);
                player.addEffect(new MobEffectInstance(MobEffects.SATURATION,30,0));
            }
            case CHARM_PRESS -> {ok=exchange(player,Items.REDSTONE,3,Items.GLOWSTONE_DUST,2);}
            case RUNE_CARVER -> {ok=exchange(player,Items.STONE,6,Items.QUARTZ,2);}
            case HOARFROST_ALTAR -> {ok=spread(level,pos,Blocks.WATER,Blocks.ICE,3);}
            case LAVA_CRUCIBLE -> {ok=spread(level,pos,Blocks.LAVA,Blocks.OBSIDIAN,2);}
            case AEOLIAN_FAN -> {
                for(ItemEntity entity:level.getEntitiesOfClass(ItemEntity.class,
                    new net.minecraft.world.phys.AABB(pos).inflate(10))) {
                    Vec3 d=entity.position().subtract(Vec3.atCenterOf(pos)).normalize().scale(1.3);
                    entity.setDeltaMovement(d.x,.5,d.z);
                }
            }
            case SUNDIAL_TOWER -> level.setDayTime(level.getDayTime()+2300);
            case BEAST_BELL -> {
                for(LivingEntity e:level.getEntitiesOfClass(LivingEntity.class,
                    new net.minecraft.world.phys.AABB(pos).inflate(8),
                    e->!(e instanceof Monster))) e.heal(6);
            }
            case RELIC_FORGE -> {ok=exchange(player,Items.AMETHYST_SHARD,8,Items.ENDER_PEARL,1);}
            case GOLD_REFINER -> {ok=exchange(player,Items.IRON_INGOT,5,Items.GOLD_INGOT,1);}
            case SOUL_RELIQUARY -> {
                if(player.experienceLevel<5)ok=false;
                else {player.giveExperienceLevels(-5);MagicEngine.refill(player,75);}
            }
        }
        if(!ok){
            if(station!=Station.RITUAL_ALTAR) inform(player,"Не хватает материала или подходящей цели.");
            return;
        }
        MagicEngine.spend(player,cost);
        LAST_USE.put(key,now+30);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
            pos.getX()+.5,pos.getY()+1.05,pos.getZ()+.5,28,.6,.5,.6,.05);
        inform(player,station.title+" · "+station.description);
    }
}
