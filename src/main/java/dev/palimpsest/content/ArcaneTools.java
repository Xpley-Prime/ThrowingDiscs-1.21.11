package dev.palimpsest.content;

import dev.palimpsest.Palimpsest;
import dev.palimpsest.magic.MagicEngine;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 32 independent, usable magical instruments. Their effects are server authoritative. */
public final class ArcaneTools {
    public enum Tool {
        BLINK_NEEDLE("blink_needle","Прыгающая игла","Пространственный рывок"),
        POLAR_FLASK("polar_flask","Полярный сосуд","Замораживает воду"),
        EMBER_STYLUS("ember_stylus","Угольное стило","Зажигает поверхность"),
        TIDE_KEY("tide_key","Ключ прилива","Создаёт источник воды"),
        STONE_SINGER("stone_singer","Певец камня","Превращает гравий в камень"),
        HOLLOW_BELL("hollow_bell","Полый колокол","Отбрасывает существ"),
        SHEPHERD_HOOK("shepherd_hook","Пастуший крюк","Притягивает существ"),
        SEED_WHISPER("seed_whisper","Шёпот семян","Ускоряет растения"),
        GLEANER_SCYTHE("gleaner_scythe","Жатва","Собирает зрелые посевы"),
        ORE_EYE("ore_eye","Око руды","Ищет руду под землёй"),
        NIGHT_LENS("night_lens","Ночная линза","Позволяет видеть в темноте"),
        SPECTRAL_MIRROR("spectral_mirror","Зеркало призраков","Подсвечивает существ"),
        SKY_FEATHER("sky_feather","Небесное перо","Замедляет падение"),
        JUMP_COIL("jump_coil","Пружина ветра","Усиливает прыжок"),
        TURTLE_KNOT("turtle_knot","Узел черепахи","Уменьшает получаемый урон"),
        HEALING_KNOT("healing_knot","Узел жизни","Восстанавливает здоровье"),
        FIRE_HEART("fire_heart","Огненное сердце","Даёт огнестойкость"),
        FROST_HEART("frost_heart","Ледяное сердце","Тушит огонь на игроке"),
        SUN_COFFER("sun_coffer","Солнечный ларец","Проясняет погоду"),
        RAIN_COFFER("rain_coffer","Дождевой ларец","Призывает дождь"),
        DAWN_DIAL("dawn_dial","Часы рассвета","Приближает рассвет"),
        DUSK_DIAL("dusk_dial","Часы сумерек","Приближает закат"),
        STORM_GONG("storm_gong","Грозовой гонг","Вызывает молнию"),
        MAGNET_CORE("magnet_core","Магнитное ядро","Притягивает выпавшие предметы"),
        WARP_THREAD("warp_thread","Нить возвращения","Запоминает точку"),
        RETURN_PIN("return_pin","Булавка пути","Возвращает к сохранённой точке"),
        INK_PHIAL("ink_phial","Сосуд чернил","Восстанавливает чернила"),
        MOB_LURE("mob_lure","Приманка созданий","Привлекает зверей"),
        BLOOD_PRISM("blood_prism","Кровавый кристалл","Меняет здоровье на чернила"),
        WISP_LANTERN("wisp_lantern","Светлячковый фонарь","Устанавливает факел"),
        EARTH_STAMP("earth_stamp","Печать почвы","Оживляет грунт"),
        BRIDGE_SPOOL("bridge_spool","Нить моста","Строит короткий мост");

        public final String id,title,description;
        Tool(String id,String title,String description) {this.id=id;this.title=title;this.description=description;}
    }
    private static final EnumMap<Tool,Item> ITEMS = new EnumMap<>(Tool.class);
    private static final Map<UUID,BlockPos> ANCHORS=new HashMap<>();
    private static final Map<UUID,String> ANCHOR_LEVELS=new HashMap<>();
    private static final Map<UUID,Long> COOLDOWNS=new HashMap<>();
    private ArcaneTools() {}

    public static void register() {
        for(Tool type:Tool.values()) {
            ResourceKey<Item> key=ResourceKey.create(Registries.ITEM,Palimpsest.id(type.id));
            Item item=Registry.register(BuiltInRegistries.ITEM,key,new RelicItem(type,new Item.Properties().setId(key).stacksTo(1)));
            ITEMS.put(type,item);
        }
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries->ITEMS.values().forEach(entries::accept));
    }

    public static void bindAnchor(ServerPlayer player,BlockPos pos) {
        ANCHORS.put(player.getUUID(),pos);
        ANCHOR_LEVELS.put(player.getUUID(),player.level().dimension().identifier().toString());
    }
    private static final class RelicItem extends Item {
        private final Tool type;
        RelicItem(Tool type,Properties properties) {super(properties);this.type=type;}
        @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if(player instanceof ServerPlayer server) invoke(server,type);
            return InteractionResult.SUCCESS;
        }
    }
    private static void say(ServerPlayer player,String message) {
        player.displayClientMessage(Component.literal("§d"+message),true);
    }
    private static boolean put(ServerLevel level,BlockPos pos,BlockState state) {
        if(!level.isLoaded(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
        if(!level.getBlockState(pos).canBeReplaced()) return false;
        return level.setBlock(pos,state,3);
    }
    private static BlockPos targeted(ServerPlayer player) {
        HitResult hit=player.pick(12,0,false);
        return hit instanceof BlockHitResult block && hit.getType()==HitResult.Type.BLOCK ? block.getBlockPos() : null;
    }
    private static void effect(ServerPlayer player,net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> kind,int ticks,int amp) {
        player.addEffect(new MobEffectInstance(kind,ticks,amp));
    }

    private static void invoke(ServerPlayer player,Tool type) {
        ServerLevel level=(ServerLevel)player.level();
        long now=level.getGameTime();
        if(COOLDOWNS.getOrDefault(player.getUUID(),-10000L)>now) {say(player,"Артефакт ещё заряжается.");return;}
        int cost=type==Tool.INK_PHIAL?0:(type==Tool.STORM_GONG?30:11);
        if(MagicEngine.mana(player)<cost && !player.getAbilities().instabuild) {
            say(player,"Недостаточно чернил: нужно "+cost);return;
        }
        BlockPos at=targeted(player);
        boolean success=true;
        switch(type) {
            case BLINK_NEEDLE -> {
                Vec3 goal=player.position().add(player.getLookAngle().scale(5));
                BlockPos ground=BlockPos.containing(goal);
                if(level.getBlockState(ground).isAir() && level.getBlockState(ground.above()).isAir())
                    player.teleportTo(goal.x,goal.y,goal.z);
                else success=false;
            }
            case POLAR_FLASK -> {
                if(at!=null && level.getBlockState(at).is(Blocks.WATER)) level.setBlock(at,Blocks.ICE.defaultBlockState(),3);
                else success=false;
            }
            case EMBER_STYLUS -> {success=at!=null && put(level,at.above(),Blocks.FIRE.defaultBlockState());}
            case TIDE_KEY -> {success=at!=null && put(level,at.above(),Blocks.WATER.defaultBlockState());}
            case STONE_SINGER -> {
                if(at!=null && level.getBlockState(at).is(Blocks.GRAVEL)) level.setBlock(at,Blocks.STONE.defaultBlockState(),3);
                else success=false;
            }
            case HOLLOW_BELL -> {
                for(LivingEntity e:level.getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(8),e->e!=player)) {
                    Vec3 push=e.position().subtract(player.position()).normalize().scale(1.7);
                    e.push(push.x,.55,push.z);
                }
            }
            case SHEPHERD_HOOK -> {
                for(LivingEntity e:level.getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(10),e->e!=player)) {
                    Vec3 pull=player.position().subtract(e.position()).normalize().scale(.8);
                    e.push(pull.x,.18,pull.z);
                }
            }
            case SEED_WHISPER -> {
                success=false;
                if(at!=null) for(BlockPos pos:BlockPos.betweenClosed(at.offset(-2,-1,-2),at.offset(2,1,2))) {
                    BlockPos p=pos.immutable();BlockState state=level.getBlockState(p);
                    if(state.getBlock() instanceof BonemealableBlock growing &&
                        growing.isValidBonemealTarget(level,p,state)) {
                        growing.performBonemeal(level,level.random,p,state);success=true;
                    }
                }
            }
            case GLEANER_SCYTHE -> {
                success=false;
                if(at!=null) for(BlockPos pos:BlockPos.betweenClosed(at.offset(-2,-1,-2),at.offset(2,1,2))) {
                    BlockPos p=pos.immutable();BlockState state=level.getBlockState(p);
                    if(state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
                        level.destroyBlock(p,true,player);success=true;
                    }
                }
            }
            case ORE_EYE -> {
                int ores=0;BlockPos origin=player.blockPosition();
                for(BlockPos pos:BlockPos.betweenClosed(origin.offset(-6,-6,-6),origin.offset(6,6,6))) {
                    String id=BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
                    if(id.endsWith("_ore") || id.contains("ancient_debris")) ores++;
                }
                say(player,"Око ощущает залежи: "+ores+" блоков поблизости.");
            }
            case NIGHT_LENS -> effect(player,MobEffects.NIGHT_VISION,20*90,0);
            case SPECTRAL_MIRROR -> {
                for(LivingEntity e:level.getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(24),e->e!=player))
                    e.addEffect(new MobEffectInstance(MobEffects.GLOWING,20*20,0));
            }
            case SKY_FEATHER -> effect(player,MobEffects.SLOW_FALLING,20*35,0);
            case JUMP_COIL -> effect(player,MobEffects.JUMP_BOOST,20*24,2);
            case TURTLE_KNOT -> effect(player,MobEffects.RESISTANCE,20*20,1);
            case HEALING_KNOT -> player.heal(8f);
            case FIRE_HEART -> effect(player,MobEffects.FIRE_RESISTANCE,20*60,0);
            case FROST_HEART -> player.clearFire();
            case SUN_COFFER -> level.setWeatherParameters(9000,0,false,false);
            case RAIN_COFFER -> level.setWeatherParameters(0,9000,true,false);
            case DAWN_DIAL -> level.setDayTime(level.getDayTime()/24000*24000+1000);
            case DUSK_DIAL -> level.setDayTime(level.getDayTime()/24000*24000+13000);
            case STORM_GONG -> {
                if(at==null) success=false;
                else {
                    level.setWeatherParameters(0,2400,true,true);
                    // Lightning requires a physical struck location.
                    level.setBlock(at.above(),Blocks.FIRE.defaultBlockState(),3);
                }
            }
            case MAGNET_CORE -> {
                for(ItemEntity entity:level.getEntitiesOfClass(ItemEntity.class,player.getBoundingBox().inflate(16))) {
                    Vec3 d=player.position().subtract(entity.position()).normalize().scale(.9);
                    entity.setDeltaMovement(d);
                }
            }
            case WARP_THREAD -> {
                ANCHORS.put(player.getUUID(),player.blockPosition());
                ANCHOR_LEVELS.put(player.getUUID(),level.dimension().identifier().toString());
            }
            case RETURN_PIN -> {
                BlockPos anchor=ANCHORS.get(player.getUUID());
                if(anchor==null || !level.dimension().identifier().toString().equals(ANCHOR_LEVELS.get(player.getUUID()))) success=false;
                else player.teleportTo(anchor.getX()+.5,anchor.getY(),anchor.getZ()+.5);
            }
            case INK_PHIAL -> {
                success=MagicEngine.mana(player)<99;
                MagicEngine.refill(player,45f);
                if(success && !player.getAbilities().instabuild) player.getMainHandItem().shrink(1);
            }
            case MOB_LURE -> {
                for(LivingEntity e:level.getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(16),e->e!=player)) {
                    Vec3 towards=player.position().subtract(e.position()).normalize();
                    e.push(towards.x*.3,0,towards.z*.3);
                }
            }
            case BLOOD_PRISM -> {
                if(player.getHealth()>4) {
                    player.hurtServer(level,player.damageSources().magic(),4);
                    MagicEngine.refill(player,36);
                } else success=false;
            }
            case WISP_LANTERN -> {success=at!=null && put(level,at.above(),Blocks.TORCH.defaultBlockState());}
            case EARTH_STAMP -> {
                if(at!=null && level.getBlockState(at).is(Blocks.DIRT)) level.setBlock(at,Blocks.GRASS_BLOCK.defaultBlockState(),3);
                else success=false;
            }
            case BRIDGE_SPOOL -> {
                success=false;BlockPos start=player.blockPosition().below();
                for(int n=1;n<=4;n++) {
                    BlockPos p=start.relative(player.getDirection(),n);
                    if(put(level,p,Blocks.GLASS.defaultBlockState())) success=true;
                }
            }
        }
        if(success) {
            MagicEngine.spend(player,cost);
            COOLDOWNS.put(player.getUUID(),now+24);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                player.getX(),player.getY()+1,player.getZ(),18,.5,.7,.5,.05);
            say(player,type.title+" · "+type.description);
        } else say(player,"Нужна подходящая цель или место.");
    }
}
