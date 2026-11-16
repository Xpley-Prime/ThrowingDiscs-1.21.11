package dev.palimpsest.client;

import dev.palimpsest.glyph.Glyph;
import dev.palimpsest.glyph.GlyphRecognizer;
import dev.palimpsest.glyph.GlyphWire;
import dev.palimpsest.glyph.Point;
import dev.palimpsest.net.CastPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;

/**
 * Handwritten magic UI. The atlas is PAGINATED and displays every real rune,
 * but never casts for the player; only manually drawn trajectories are sent.
 */
public final class GlyphScreen extends Screen {
    private static final int BACK=0xF00B1424,PANEL=0xE51B2840,BORDER=0xFF6B82AA;
    private static final int GOLD=0xFFF1D49D,CYAN=0xFF9CE6FF,FAINT=0xFFA3AEC3;
    private static final int SUCCESS=0xFF83F3C5,FAIL=0xFFFF9A97;
    private final List<List<Point>> strokes=new ArrayList<>();
    private final List<GlyphRecognizer.Match> recognized=new ArrayList<>();
    private final List<Point> active=new ArrayList<>();
    private boolean dragging,atlas;
    private int canvasX,canvasY,canvasSize,atlasRole,atlasPage,atlasSelected;
    private String feedback="Проведи один непрерывный росчерк";
    private int feedbackColor=FAINT;

    public GlyphScreen(){super(Component.literal("Палимпсест · письмо миру"));}

    @Override protected void init(){
        boolean wide=width>=690;
        int targetW=wide?width-350:width-36;
        canvasSize=Math.max(65,Math.min(290,Math.min(targetW,height-125)));
        canvasX=wide?(width-canvasSize)/2:Math.max(10,(width-canvasSize)/2);
        canvasY=Math.max(52,(height-canvasSize)/2-5);
        canvasY=Math.min(canvasY,Math.max(49,height-74-canvasSize));
    }
    @Override public boolean isPauseScreen(){return false;}

    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float delta){
        g.fill(0,0,width,height,0xE6081020);
        g.drawCenteredString(font,clip("✦ PALIMPSEST ✦  ЧЕРНИЛА МИРА",width-16),
            width/2,10,GOLD);
        g.drawCenteredString(font,clip("Рисунок ≠ кнопка: каждая руна — твой собственный штрих",width-16),
            width/2,24,FAINT);
        if(atlas){renderAtlas(g);return;}
        int x=canvasX,y=canvasY,size=canvasSize,cx=x+size/2,cy=y+size/2;
        g.fill(x-7,y-7,x+size+7,y+size+7,0xCC111E35);
        g.fill(x-3,y-3,x+size+3,y+size+3,BORDER);
        g.fill(x,y,x+size,y+size,BACK);
        for(int i=1;i<8;i++){
            int gx=x+i*size/8,gy=y+i*size/8;
            g.fill(gx,y,gx+1,y+size,0x244F7B9D);
            g.fill(x,gy,x+size,gy+1,0x244F7B9D);
        }
        g.fill(cx-1,y+5,cx+1,y+14,CYAN);
        g.fill(cx-1,y+size-14,cx+1,y+size-5,CYAN);
        g.fill(x+5,cy-1,x+14,cy+1,CYAN);
        g.fill(x+size-14,cy-1,x+size-5,cy+1,CYAN);
        if(active.size()>1){
            drawStroke(g,active,x,y,size,0x3555CEFF,4);
            drawStroke(g,active,x,y,size,0xFFACEEFF,1);
        }
        if(!dragging&&active.isEmpty()){
            g.drawCenteredString(font,clip("✧",size),cx,cy-19,0xFF668AAB);
            g.drawCenteredString(font,clip("РИСУЙ ЗДЕСЬ",size-9),cx,cy+1,0xFF779ABA);
        }

        Glyph.Role role=roleFor(strokes.size());
        String step=strokes.size()>=3?"3/3 · ФОРМУЛА ГОТОВА":
            (strokes.size()+1)+"/3 · "+roleLabel(role);
        g.drawCenteredString(font,clip(step,size+22),cx,y-23,CYAN);
        g.drawCenteredString(font,clip(feedback,Math.min(width-20,size+125)),
            cx,y+size+14,feedbackColor);

        int leftW=x-22,rightX=x+size+20,rightW=width-rightX-12;
        if(leftW>=126)renderWritten(g,10,y,Math.min(190,leftW));
        if(rightW>=147)renderTeaser(g,rightX,y,rightW,role);
        if(leftW<126&&rightW<147)
            g.drawCenteredString(font,clip("Tab — все руны и образцы",width-20),
                width/2,Math.min(height-52,y+size+28),GOLD);

        renderFooter(g);
        super.render(g,mouseX,mouseY,delta);
    }
    private void renderWritten(GuiGraphics g,int x,int y,int w){
        g.drawString(font,clip("ТВОЯ ФОРМУЛА",w),x,y,GOLD);
        for(int i=0;i<3;i++){
            int ty=y+20+i*40;
            Glyph.Role role=roleFor(i);
            String name=i<recognized.size()?recognized.get(i).glyph().display():roleLabel(role);
            int color=i<recognized.size()?recognized.get(i).glyph().color():0xFF74839E;
            g.drawString(font,clip((i+1)+". "+name,w),x,ty,color);
            if(i<recognized.size()){
                g.drawString(font,clip(Math.round(100*recognized.get(i).confidence())+
                    "%  распознано",w-10),x+10,ty+14,FAINT);
            }else g.drawString(font,clip(i==2?"Необязательный штрих":"Ожидает росчерк",w-10),
                x+10,ty+14,FAINT);
        }
    }
    private void renderTeaser(GuiGraphics g,int x,int y,int w,Glyph.Role role){
        g.drawString(font,clip("АТЛАС · "+roleLabel(role),w),x,y,GOLD);
        List<Glyph> examples=Glyph.ofRole(role);
        g.drawString(font,clip("Tab — все "+examples.size()+" знаков",w),x,y+15,FAINT);
        int show=Math.min(3,examples.size());
        for(int i=0;i<show;i++){
            Glyph glyph=examples.get(i);
            int yy=y+37+i*57,sz=43;
            if(yy+sz>height-56)break;
            g.fill(x,yy,x+sz,yy+sz,0xFF162039);
            drawStroke(g,glyph.template(),x+4,yy+4,sz-8,glyph.color(),1);
            drawOrigin(g,glyph.template(),x+4,yy+4,sz-8);
            g.drawString(font,clip(glyph.display(),w-sz-8),x+sz+8,yy+8,glyph.color());
            g.drawString(font,clip("→ первый: "+roleLabel(role),w-sz-8),
                x+sz+8,yy+22,FAINT);
        }
    }
    private void renderFooter(GuiGraphics g){
        g.fill(0,height-52,width,height,0xF20C1528);
        g.drawCenteredString(font,clip("ЧЕРНИЛА  "+PalimpsestClient.mana+" / 100",width-15),
            width/2,height-48,CYAN);
        g.drawCenteredString(font,clip("Enter — сотворить    Backspace — назад    R — очистить",width-15),
            width/2,height-32,0xFFE3E8F5);
        g.drawCenteredString(font,clip("Tab — полный атлас    Esc — закрыть",width-15),
            width/2,height-17,FAINT);
    }

    private int atlasRows(){return Math.max(1,Math.min(8,(height-145)/54));}
    private int atlasPages(){
        return Math.max(1,(Glyph.ofRole(switch(atlasRole){
            case 1->Glyph.Role.ELEMENT;case 2->Glyph.Role.INFLECTION;default->Glyph.Role.INTENT;
        }).size()+atlasRows()-1)/atlasRows());
    }
    private void setAtlasRole(int n){atlasRole=Math.clamp(n,0,2);atlasPage=0;atlasSelected=0;}
    private void moveAtlas(int delta){
        atlasPage=Math.clamp(atlasPage+delta,0,atlasPages()-1);
        atlasSelected=atlasPage*atlasRows();
    }
    private Glyph.Role atlasCurrentRole(){
        return switch(atlasRole){case 1->Glyph.Role.ELEMENT;case 2->Glyph.Role.INFLECTION;default->Glyph.Role.INTENT;};
    }
    private void renderAtlas(GuiGraphics g){
        int x=10,y=45,w=width-20,h=Math.max(60,height-100);
        g.fill(x,y,x+w,y+h,PANEL);
        String[] tabs={"1 · НАМЕРЕНИЕ","2 · СТИХИЯ","3 · ОТТЕНОК"};
        int tabWidth=Math.max(1,w/3);
        for(int i=0;i<3;i++){
            int tx=x+i*tabWidth;
            g.fill(tx,y,tx+tabWidth-1,y+24,i==atlasRole?0xFF405A77:0xFF1A2A42);
            g.drawCenteredString(font,clip(tabs[i],tabWidth-8),tx+tabWidth/2,y+8,
                i==atlasRole?GOLD:FAINT);
        }
        List<Glyph> all=Glyph.ofRole(atlasCurrentRole());
        int rowCount=atlasRows();
        int listX=x+8,detailsX=x+Math.max(240,w/2);
        boolean twoColumns=w>=545;
        int listW=twoColumns?detailsX-listX-12:w-16;
        for(int i=0;i<rowCount;i++){
            int idx=atlasPage*rowCount+i;
            if(idx>=all.size())break;
            int yy=y+31+i*54;
            if(yy+48>y+h-7)break;
            Glyph glyph=all.get(idx);
            g.fill(listX,yy,listX+listW,yy+48,idx==atlasSelected?0xFF354D69:0xFF1A2A43);
            g.fill(listX+3,yy+3,listX+46,yy+45,0xFF0A1728);
            drawStroke(g,glyph.template(),listX+7,yy+7,35,glyph.color(),1);
            drawOrigin(g,glyph.template(),listX+7,yy+7,35);
            int textW=Math.max(30,listW-60);
            g.drawString(font,clip((idx+1)+". "+glyph.display(),textW),
                listX+53,yy+8,glyph.color());
            g.drawString(font,clip(glyph.hint(),textW),
                listX+53,yy+25,FAINT);
        }
        if(twoColumns && atlasSelected<all.size()){
            Glyph chosen=all.get(atlasSelected);
            int preview=Math.min(180,Math.min(w-(detailsX-x)-25,h-135));
            g.drawString(font,clip("ИЗУЧИ ЖЕСТ",w-(detailsX-x)-10),
                detailsX,y+39,GOLD);
            g.drawString(font,clip(chosen.display(),w-(detailsX-x)-10),
                detailsX,y+59,chosen.color());
            if(preview>=50){
                int px=detailsX+Math.max(0,(w-(detailsX-x)-preview)/2),py=y+85;
                g.fill(px-5,py-5,px+preview+5,py+preview+5,0xFF0B1427);
                drawStroke(g,chosen.template(),px+6,py+6,preview-12,chosen.color(),2);
                drawOrigin(g,chosen.template(),px+6,py+6,preview-12);
            }
            int captionY=y+84+Math.max(0,preview)+18;
            if(captionY+27<y+h){
                g.drawString(font,clip("Золото — точка начала",w-(detailsX-x)-10),
                    detailsX,captionY,GOLD);
                g.drawString(font,clip("Веди без отрыва ЛКМ",w-(detailsX-x)-10),
                    detailsX,captionY+15,FAINT);
            }
        }
        g.drawCenteredString(font,clip("←  СТРАНИЦА "+(atlasPage+1)+"/"+atlasPages()+"  →",width-16),
            width/2,height-45,GOLD);
        g.drawCenteredString(font,clip("1–3 разделы · ← → листать · Tab / Esc назад",width-16),
            width/2,height-25,FAINT);
    }
    private void drawStroke(GuiGraphics g,List<Point> pts,int x,int y,int size,int color,int thickness){
        for(int i=1;i<pts.size();i++){
            Point a=pts.get(i-1),b=pts.get(i);
            double x0=x+a.x()*size,y0=y+a.y()*size;
            double x1=x+b.x()*size,y1=y+b.y()*size;
            int steps=Math.min(400,Math.max(1,(int)Math.ceil(Math.hypot(x1-x0,y1-y0))));
            for(int k=0;k<=steps;k++){
                double t=k/(double)steps;
                int px=(int)Math.round(x0+(x1-x0)*t),py=(int)Math.round(y0+(y1-y0)*t);
                g.fill(px-thickness,py-thickness,px+thickness+1,py+thickness+1,color);
            }
        }
    }
    private void drawOrigin(GuiGraphics g,List<Point> pts,int x,int y,int size){
        Point p=pts.getFirst();
        int ox=(int)(x+p.x()*size),oy=(int)(y+p.y()*size);
        g.fill(ox-3,oy-3,ox+4,oy+4,0xFFFFDE8D);
    }
    private String clip(String s,int max){
        if(max<=12)return "";
        if(font.width(s)<=max)return s;
        while(!s.isEmpty()&&font.width(s+"…")>max)s=s.substring(0,s.length()-1);
        return s+"…";
    }
    private static Glyph.Role roleFor(int index){
        return index<=0?Glyph.Role.INTENT:index==1?Glyph.Role.ELEMENT:Glyph.Role.INFLECTION;
    }
    private static String roleLabel(Glyph.Role r){
        return switch(r){case INTENT->"НАМЕРЕНИЕ";case ELEMENT->"СТИХИЯ";case INFLECTION->"ОТТЕНОК";};
    }
    private boolean inside(double mx,double my){
        return mx>=canvasX&&mx<=canvasX+canvasSize&&my>=canvasY&&my<=canvasY+canvasSize;
    }
    private void addPoint(double mx,double my){
        double nx=Math.clamp((mx-canvasX)/(double)canvasSize,0,1);
        double ny=Math.clamp((my-canvasY)/(double)canvasSize,0,1);
        Point p=new Point(nx,ny);
        if(!active.isEmpty()){
            Point last=active.getLast();
            double d=last.distance(p);
            if(d<.004)return;
            int steps=Math.min(16,Math.max(1,(int)Math.ceil(d/.016)));
            for(int i=1;i<=steps;i++){
                double t=i/(double)steps;
                active.add(new Point(last.x()+(p.x()-last.x())*t,last.y()+(p.y()-last.y())*t));
            }
        }else active.add(p);
        while(active.size()>GlyphRecognizer.MAX_POINTS){
            List<Point> reduced=new ArrayList<>();
            for(int i=0;i<active.size();i+=2)reduced.add(active.get(i));
            if(!active.getLast().equals(reduced.getLast()))reduced.add(active.getLast());
            active.clear();active.addAll(reduced);
        }
    }
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean doubleClick){
        if(atlas){
            if(e.button()!=GLFW.GLFW_MOUSE_BUTTON_LEFT)return super.mouseClicked(e,doubleClick);
            int x=10,y=45,w=width-20;
            if(e.y()>=y&&e.y()<y+25){
                setAtlasRole(Math.clamp(((int)e.x()-x)/Math.max(1,w/3),0,2));
                return true;
            }
            if(e.y()>=height-55){
                moveAtlas(e.x()<width/2?-1:1);return true;
            }
            if(e.x()>=x+8&&e.y()>y+31&&e.y()<y+31+atlasRows()*54){
                int idx=atlasPage*atlasRows()+((int)e.y()-y-31)/54;
                if(idx<Glyph.ofRole(atlasCurrentRole()).size())atlasSelected=idx;
                return true;
            }
            return true;
        }
        if(e.button()==GLFW.GLFW_MOUSE_BUTTON_LEFT&&strokes.size()<3&&inside(e.x(),e.y())){
            active.clear();dragging=true;addPoint(e.x(),e.y());return true;
        }
        return super.mouseClicked(e,doubleClick);
    }
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){
        if(dragging){addPoint(e.x(),e.y());return true;}
        return super.mouseDragged(e,dx,dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent e){
        if(dragging&&e.button()==GLFW.GLFW_MOUSE_BUTTON_LEFT){
            dragging=false;addPoint(e.x(),e.y());
            if(GlyphRecognizer.validStroke(active)){
                GlyphRecognizer.Match m=GlyphRecognizer.recognize(active,roleFor(strokes.size()));
                if(m.accepted()){
                    strokes.add(List.copyOf(active));recognized.add(m);
                    feedback="✧ "+m.glyph().display()+" · "+Math.round(m.confidence()*100)+"% распознано";
                    feedbackColor=SUCCESS;
                    if(strokes.size()==2)feedback="Два знака готовы: Enter или третий оттенок";
                }else{
                    feedback=m.glyph()==null?"Не узнано. Посмотри образец по Tab":
                        "Почти "+m.glyph().display()+". Повтори ближе к образцу";
                    feedbackColor=FAIL;
                }
            }else{
                feedback="Короткий штрих. Рисуй крупнее, не отрывая ЛКМ";
                feedbackColor=FAIL;
            }
            active.clear();return true;
        }
        return super.mouseReleased(e);
    }
    @Override public boolean keyPressed(KeyEvent key){
        if(key.key()==GLFW.GLFW_KEY_TAB){atlas=!atlas;return true;}
        if(atlas){
            switch(key.key()){
                case GLFW.GLFW_KEY_1,GLFW.GLFW_KEY_KP_1 -> setAtlasRole(0);
                case GLFW.GLFW_KEY_2,GLFW.GLFW_KEY_KP_2 -> setAtlasRole(1);
                case GLFW.GLFW_KEY_3,GLFW.GLFW_KEY_KP_3 -> setAtlasRole(2);
                case GLFW.GLFW_KEY_LEFT,GLFW.GLFW_KEY_PAGE_UP -> moveAtlas(-1);
                case GLFW.GLFW_KEY_RIGHT,GLFW.GLFW_KEY_PAGE_DOWN -> moveAtlas(1);
                case GLFW.GLFW_KEY_ESCAPE -> atlas=false;
                default -> {}
            }
            return true;
        }
        if(key.key()==GLFW.GLFW_KEY_ENTER||key.key()==GLFW.GLFW_KEY_KP_ENTER){
            if(strokes.size()>=2)cast();
            else {feedback="Сначала нарисуй намерение и стихию";feedbackColor=FAIL;}
            return true;
        }
        if(key.key()==GLFW.GLFW_KEY_BACKSPACE){
            if(!strokes.isEmpty()){strokes.removeLast();recognized.removeLast();}
            active.clear();dragging=false;feedback="Последняя руна стёрта";feedbackColor=FAINT;return true;
        }
        if(key.key()==GLFW.GLFW_KEY_R){
            strokes.clear();recognized.clear();active.clear();dragging=false;
            feedback="Новая страница готова";feedbackColor=FAINT;return true;
        }
        return super.keyPressed(key);
    }
    private void cast(){
        try{
            ClientPlayNetworking.send(new CastPayload(GlyphWire.encode(strokes)));
            onClose();
        }catch(IllegalArgumentException e){
            feedback="Ошибка передачи рисунка. Уменьши число точек";
            feedbackColor=FAIL;
        }
    }
}
