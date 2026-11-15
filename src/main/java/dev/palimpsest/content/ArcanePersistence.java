package dev.palimpsest.content;

import dev.palimpsest.magic.MagicEngine;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/** World-local, crash-resilient persistence for ink, waypoints and last performed ritual. */
public final class ArcanePersistence {
    private static int saves;
    private ArcanePersistence() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(ArcanePersistence::load);
        ServerLifecycleEvents.SERVER_STOPPING.register(ArcanePersistence::save);
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(++saves>=2400){saves=0;save(server);}
        });
    }
    private static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("palimpsest").resolve("state.properties");
    }

    private static void load(MinecraftServer server) {
        saves=0;
        Properties data=new Properties();
        Path file=file(server);
        if(Files.isRegularFile(file)){
            try(InputStream in=Files.newInputStream(file)){data.load(in);}
            catch(IOException e){System.err.println("[Palimpsest] Unable to load world state: "+e.getMessage());}
        }
        Map<UUID,Float> ink=new HashMap<>();
        Map<UUID,ArcaneTools.Anchor> anchors=new HashMap<>();
        Map<UUID,ArcaneRituals.Ritual> rituals=new HashMap<>();
        for(String name:data.stringPropertyNames()) {
            try {
                String[] key=name.split("[.]",2);
                if(key.length!=2)continue;
                UUID player=UUID.fromString(key[1]);
                String value=data.getProperty(name);
                switch(key[0]){
                    case "ink" -> ink.put(player,Math.clamp(Float.parseFloat(value),0f,100f));
                    case "anchor" -> {
                        String[] pieces=value.split("[|]");
                        if(pieces.length==4){
                            BlockPos pos=new BlockPos(Integer.parseInt(pieces[1]),
                                Integer.parseInt(pieces[2]),Integer.parseInt(pieces[3]));
                            anchors.put(player,new ArcaneTools.Anchor(pieces[0],pos));
                        }
                    }
                    case "ritual" -> rituals.put(player,ArcaneRituals.Ritual.valueOf(value));
                }
            }catch(IllegalArgumentException ignored){
                System.err.println("[Palimpsest] Ignoring invalid persisted state: "+name);
            }
        }
        MagicEngine.loadInk(ink);
        ArcaneTools.loadAnchors(anchors);
        ArcaneRituals.loadHistory(rituals);
        System.out.println("[Palimpsest] Loaded "+ink.size()+" ink records and "+anchors.size()+" waypoints.");
    }
    private static void save(MinecraftServer server) {
        Path target=file(server),temporary=target.resolveSibling("state.properties.tmp");
        Properties data=new Properties();
        MagicEngine.snapshotInk().forEach((id,value)->data.setProperty("ink."+id,value.toString()));
        ArcaneTools.snapshotAnchors().forEach((id,anchor)->{
            BlockPos p=anchor.position();
            data.setProperty("anchor."+id,anchor.dimension()+"|"+p.getX()+"|"+p.getY()+"|"+p.getZ());
        });
        ArcaneRituals.snapshotHistory().forEach((id,r)->data.setProperty("ritual."+id,r.name()));
        try {
            Files.createDirectories(target.getParent());
            try(OutputStream out=Files.newOutputStream(temporary)){data.store(out,"Palimpsest world-local magic state");}
            try{Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(java.nio.file.AtomicMoveNotSupportedException e){
                Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);
            }
        }catch(IOException e){
            System.err.println("[Palimpsest] Unable to save world state: "+e.getMessage());
        }
    }
}
