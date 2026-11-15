package dev.palimpsest.content;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/** Read-only, paginated /palimpsest commands for all implemented content. */
public final class ArcaneCatalogue {
    private ArcaneCatalogue() {}
    private static final int PAGE_SIZE=8;
    public static final int COUNT=ArcaneTools.Tool.values().length
        +ArcaneStations.Station.values().length+ArcaneRituals.Ritual.values().length;

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->
            dispatcher.register(Commands.literal("palimpsest")
                .then(Commands.literal("catalogue").executes(ctx->{
                    say(ctx.getSource(),"§dАрхив Палимпсеста: §b"+
                        ArcaneTools.Tool.values().length+" инструментов, "+
                        ArcaneStations.Station.values().length+" сооружений, "+
                        ArcaneRituals.Ritual.values().length+" ритуалов (§e"+COUNT+"§b механик).");
                    say(ctx.getSource(),"§7Книга в игре: K. Команды: /palimpsest tools | stations | rituals [страница]");
                    return 1;
                }))
                .then(Commands.literal("tools")
                    .executes(ctx->list(ctx.getSource(),0,1))
                    .then(Commands.argument("page",IntegerArgumentType.integer(1))
                        .executes(ctx->list(ctx.getSource(),0,IntegerArgumentType.getInteger(ctx,"page")))))
                .then(Commands.literal("stations")
                    .executes(ctx->list(ctx.getSource(),1,1))
                    .then(Commands.argument("page",IntegerArgumentType.integer(1))
                        .executes(ctx->list(ctx.getSource(),1,IntegerArgumentType.getInteger(ctx,"page")))))
                .then(Commands.literal("rituals")
                    .executes(ctx->list(ctx.getSource(),2,1))
                    .then(Commands.argument("page",IntegerArgumentType.integer(1))
                        .executes(ctx->list(ctx.getSource(),2,IntegerArgumentType.getInteger(ctx,"page")))))
            )
        );
    }
    private static void say(CommandSourceStack source,String msg) {
        source.sendSuccess(()->Component.literal(msg),false);
    }
    private static int list(CommandSourceStack source,int tab,int page) {
        int count=switch(tab) {
            case 0->ArcaneTools.Tool.values().length;
            case 1->ArcaneStations.Station.values().length;
            default->ArcaneRituals.Ritual.values().length;
        };
        int pages=(count+PAGE_SIZE-1)/PAGE_SIZE;
        if(page>pages){say(source,"§cВсего "+pages+" страниц.");return 0;}
        String kind=switch(tab){case 0->"Инструменты";case 1->"Сооружения";default->"Ритуалы";};
        say(source,"§6"+kind+" §7— страница "+page+"/"+pages);
        int start=(page-1)*PAGE_SIZE,end=Math.min(count,start+PAGE_SIZE);
        for(int i=start;i<end;i++){
            String name,detail;
            if(tab==0){
                var t=ArcaneTools.Tool.values()[i];
                name=t.title;detail=t.description+" §8(/give @s palimpsest:"+t.id+")";
            }else if(tab==1){
                var s=ArcaneStations.Station.values()[i];
                name=s.title;detail=s.description+" §8(/give @s palimpsest:"+s.id+")";
            }else{
                var r=ArcaneRituals.Ritual.values()[i];
                name=r.name;
                detail=r.description+" §8["+net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(r.catalyst)+"]";
            }
            say(source,"§b"+(i+1)+". "+name+" §7— "+detail);
        }
        return 1;
    }
}
