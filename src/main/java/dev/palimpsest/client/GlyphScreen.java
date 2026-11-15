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

/** The player inks glyphs themselves. Templates on the side are REFERENCE ONLY, never buttons. */
public final class GlyphScreen extends Screen {
    private static final int BACK=0xE316192B;
    private static final int FRAME=0xFF746C9B;
    private static final int GLOW=0xFFB6EEFF;
    private static final int MUTED=0xFF9B9AB6;
    private final List<List<Point>> strokes=new ArrayList<>();
    private final List<GlyphRecognizer.Match> recognized=new ArrayList<>();
    private final List<Point> active=new ArrayList<>();
    private String feedback="Рисуй левою кнопкой мыши";
    private int feedbackColor=MUTED;
    private boolean dragging;
    private int canvasX,canvasY,canvasSize;
    private boolean atlasOpen;
    private int atlasPage;

    public GlyphScreen() {super(Component.literal("Палимпсест · Письмена"));}
    @Override protected void init() {
        // Screen space is measured in logical GUI pixels, NOT physical monitor pixels.
        // Keep every widget inside the viewport for any Minecraft GUI scale.
        int availableHeight=Math.max(96,height-136);
        boolean threeColumns=width>=720;
        int availableWidth=threeColumns?width-400:width-36;
        canvasSize=Math.max(90,Math.min(285,Math.min(availableWidth,availableHeight)));
        canvasX=threeColumns?Math.max(150,(width-canvasSize)/2-16):(width-canvasSize)/2;
        canvasY=56+Math.max(0,(Math.max(canvasSize,availableHeight)-canvasSize)/2);
        canvasY=Math.min(canvasY,Math.max(56,height-70-canvasSize));
    }
    @Override public boolean isPauseScreen() {return false;}

    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float delta) {
        g.fill(0,0,width,height,0xCD090C18);
        final int gold=0xFFF0D6A6;
        String heading="✦ PALIMPSEST · ЧЕРНИЛА МИРА ✦";
        g.drawCenteredString(font,fit(heading,width-20),width/2,9,gold);
        g.drawCenteredString(font,fit("Живая каллиграфия: каждый штрих создаё магию",width-20),
            width/2,23,MUTED);

        if(atlasOpen) {
            int panelX=12,panelY=54,panelW=width-24,panelH=Math.max(90,height-119);
            g.fill(panelX,panelY,panelX+panelW,panelY+panelH,0xEF13192D);
            Glyph.Role role=switch(atlasPage){case 1->Glyph.Role.ELEMENT;case 2->Glyph.Role.INFLECTION;default->Glyph.Role.INTENT;};
            drawLexicon(g,panelX+12,panelY+11,panelW-24,panelH-22,role);
            g.drawCenteredString(font,"1 — намерение     2 — стихия     3 — оттенок     Tab — рисование",
                width/2,height-20,0xFFCBD1DC);
            return;
        }

        int x=canvasX,y=canvasY,cx=x+canvasSize/2,cy=y+canvasSize/2;
        g.fill(x-7,y-7,x+canvasSize+7,y+canvasSize+7,0xD3151A30);
        g.fill(x-2,y-2,x+canvasSize+2,y+canvasSize+2,FRAME);
        g.fill(x,y,x+canvasSize,y+canvasSize,BACK);
        for(int i=1;i<8;i++) {
            int gx=x+canvasSize*i/8,gy=y+canvasSize*i/8;
            g.fill(gx,y,gx+1,y+canvasSize,0x2E8497B7);
            g.fill(x,gy,x+canvasSize,gy+1,0x2E8497B7);
        }
        g.fill(cx-1,y+8,cx+1,y+16,0x9995BCE7);
        g.fill(cx-1,y+canvasSize-16,cx+1,y+canvasSize-8,0x9995BCE7);
        g.fill(x+8,cy-1,x+16,cy+1,0x9995BCE7);
        g.fill(x+canvasSize-16,cy-1,x+canvasSize-8,cy+1,0x9995BCE7);
        if(active.size()>1) renderPath(g,active,x,y,canvasSize,0xFFC3F4FF,2);
        if(!dragging && active.isEmpty()) {
            g.drawCenteredString(font,"✧",cx,cy-13,0x6684BCE6);
            g.drawCenteredString(font,fit("НАЧЕРТАЙ СИМВОЛ",canvasSize-10),cx,cy+3,0xFF719DBB);
        }
        Glyph.Role current=roleFor(Math.min(2,strokes.size()));
        String phase=strokes.size()>=3?"ФОРМУЛА ГОТОВА":(strokes.size()+1)+"/3 · "+switch(current){
            case INTENT->"НАМЕРЕНИЕ";case ELEMENT->"СТИХИЯ";case INFLECTION->"ОТТЕНОК (необязательно)";};
        g.drawCenteredString(font,fit(phase,canvasSize+28),cx,y-24,GLOW);
        g.drawCenteredString(font,fit(feedback,Math.max(80,Math.min(width-20,canvasSize+110))),
            cx,y+canvasSize+12,feedbackColor);

        int rightX=x+canvasSize+21,rightW=width-rightX-11;
        if(rightW>=155) {
            drawLexicon(g,rightX,y-6,rightW,Math.min(height-100,canvasSize+25),current);
        } else {
            g.drawCenteredString(font,"Tab — атлас всех рун",width/2,
                Math.min(height-63,y+canvasSize+27),gold);
        }
        int leftW=x-20;
        if(leftW>=120) drawFormula(g,12,y,Math.min(leftW-10,220));
        drawFooter(g);
        super.render(g,mouseX,mouseY,delta);
    }

    private void drawFooter(GuiGraphics g) {
        g.fill(0,height-55,width,height,0xEF111629);
        String mana="ЧЕРНИЛА "+PalimpsestClient.mana+" / 100";
        g.drawCenteredString(font,mana,width/2,height-51,GLOW);
        g.drawCenteredString(font,fit("Enter — исполнить    Backspace — назад    R — очистить",width-12),
            width/2,height-35,0xFFCBD1DC);
        g.drawCenteredString(font,fit("Esc — закрыть    Tab — полный атлас",width-12),
            width/2,height-20,0xFFCBD1DC);
    }

    private void drawFormula(GuiGraphics g,int x,int y,int w) {
        g.drawString(font,fit("ЗАПИСАННЫЕ РУНЫ",w),x,y,0xFFE2C998,true);
        if(recognized.isEmpty()) g.drawString(font,fit("Пока пусто",w),x,y+20,MUTED);
        for(int i=0;i<recognized.size();i++) {
            Glyph glyph=recognized.get(i).glyph();
            g.drawString(font,fit((i+1)+". "+glyph.display(),w),x,y+22+i*36,glyph.color(),true);
            g.drawString(font,fit(Math.round(100*recognized.get(i).confidence())+"% точность",w-8),
                x+8,y+35+i*36,MUTED,false);
        }
        if(strokes.size()>=2) g.drawString(font,fit("Enter — сотворить",w),x,y+148,0xFFB6E7B4);
    }

    private void drawLexicon(GuiGraphics g,int x,int y,int w,int h,Glyph.Role role) {
        g.drawString(font,fit("АТЛАС НАЧЕРТАНИЙ",w),x,y,0xFFF0D6A6,true);
        g.drawString(font,fit("Начинай с золотой точки",w),x,y+14,MUTED);
        List<Glyph> glyphs=Glyph.ofRole(role);
        int rowH=Math.max(42,Math.min(61,(h-39)/Math.max(1,glyphs.size())));
        for(int i=0;i<glyphs.size();i++) {
            Glyph glyph=glyphs.get(i);
            int yy=y+36+i*rowH;
            if(yy+39>y+h) break;
            int tile=Math.min(40,rowH-5),tx=x+2;
            g.fill(tx,yy,tx+tile,yy+tile,0xAA1C1D32);
            renderPath(g,glyph.template(),tx+3,yy+3,tile-6,glyph.color(),1);
            Point origin=glyph.template().getFirst();
            int sx=(int)(tx+3+origin.x()*(tile-6)),sy=(int)(yy+3+origin.y()*(tile-6));
            g.fill(sx-2,sy-2,sx+2,sy+2,0xFFFFD56B);
            int textX=x+tile+10,textW=Math.max(20,w-tile-11);
            g.drawString(font,fit(glyph.display(),textW),textX,yy+6,glyph.color(),true);
            g.drawString(font,fit(glyph.hint(),textW),textX,yy+20,MUTED);
        }
    }

    private String fit(String s,int maxWidth) {
        if(maxWidth<=12)return "";
        if(font.width(s)<=maxWidth)return s;
        String suffix="…";
        while(!s.isEmpty() && font.width(s+suffix)>maxWidth) s=s.substring(0,s.length()-1);
        return s+suffix;
    }

    /** Draw continuous segments using GUI rectangles without special shaders or mixins. */
    private void renderPath(GuiGraphics g,List<Point> pts,int x,int y,int size,int color,int thickness) {
        for(int i=1;i<pts.size();i++) {
            Point a=pts.get(i-1),b=pts.get(i);
            double ax=x+a.x()*size,ay=y+a.y()*size;
            double bx=x+b.x()*size,by=y+b.y()*size;
            int steps=Math.min(300,Math.max(1,(int)Math.ceil(Math.hypot(bx-ax,by-ay))));
            for(int k=0;k<=steps;k++) {
                double t=k/(double)steps;
                int px=(int)Math.round(ax+(bx-ax)*t),py=(int)Math.round(ay+(by-ay)*t);
                g.fill(px-thickness,py-thickness,px+thickness+1,py+thickness+1,color);
            }
        }
    }

    private static Glyph.Role roleFor(int i) {
        return switch(i) {case 0 -> Glyph.Role.INTENT;case 1 -> Glyph.Role.ELEMENT;default -> Glyph.Role.INFLECTION;};
    }
    private boolean inside(double x,double y) {
        return x>=canvasX && x<=canvasX+canvasSize && y>=canvasY && y<=canvasY+canvasSize;
    }
    private void addPoint(double x,double y) {
        double px=Math.max(0,Math.min(1,(x-canvasX)/canvasSize));
        double py=Math.max(0,Math.min(1,(y-canvasY)/canvasSize));
        Point point=new Point(px,py);
        if(!active.isEmpty() && active.getLast().distance(point)<.005) return;
        active.add(point);
        if(active.size()>GlyphRecognizer.MAX_POINTS) {
            // Cap packet volume while retaining the entire continuous trace.
            List<Point> reduced=new ArrayList<>();
            for(int i=0;i<active.size();i+=2) reduced.add(active.get(i));
            active.clear();active.addAll(reduced);
        }
    }

    @Override public boolean mouseClicked(MouseButtonEvent e,boolean dblClick) {
        if(e.button()==GLFW.GLFW_MOUSE_BUTTON_LEFT && strokes.size()<3 && inside(e.x(),e.y())) {
            active.clear();dragging=true;addPoint(e.x(),e.y());return true;
        }
        return super.mouseClicked(e,dblClick);
    }
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy) {
        if(dragging) { addPoint(e.x(),e.y());return true; }
        return super.mouseDragged(e,dx,dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent e) {
        if(dragging && e.button()==GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragging=false;addPoint(e.x(),e.y());
            if(GlyphRecognizer.validStroke(active)) {
                GlyphRecognizer.Match match=GlyphRecognizer.recognize(active,roleFor(strokes.size()));
                if(match.accepted()) {
                    strokes.add(List.copyOf(active));recognized.add(match);
                    feedback="✧ "+match.glyph().display()+" · "+(int)(100*match.confidence())+"%";
                    feedbackColor=match.glyph().color();
                    if(strokes.size()==2) feedback+="  (Enter — исполнить / нарисуй третий)";
                } else {
                    feedback="Руна не узнана. Нарисуй крупнее и повтори.";
                    feedbackColor=0xFFFF9292;
                }
            } else {
                feedback="Линия слишком короткая. Используй всю площадь квадрата.";
                feedbackColor=0xFFFF9292;
            }
            active.clear();return true;
        }
        return super.mouseReleased(e);
    }
    @Override public boolean keyPressed(KeyEvent key) {
        if(key.key()==GLFW.GLFW_KEY_TAB) { atlasOpen=!atlasOpen;return true; }
        if(atlasOpen) {
            if(key.key()==GLFW.GLFW_KEY_1 || key.key()==GLFW.GLFW_KEY_KP_1) atlasPage=0;
            if(key.key()==GLFW.GLFW_KEY_2 || key.key()==GLFW.GLFW_KEY_KP_2) atlasPage=1;
            if(key.key()==GLFW.GLFW_KEY_3 || key.key()==GLFW.GLFW_KEY_KP_3) atlasPage=2;
            if(key.key()==GLFW.GLFW_KEY_ESCAPE) atlasOpen=false;
            return true;
        }
        if(key.key()==GLFW.GLFW_KEY_ENTER || key.key()==GLFW.GLFW_KEY_KP_ENTER) {
            if(strokes.size()>=2) cast();return true;
        }
        if(key.key()==GLFW.GLFW_KEY_BACKSPACE) {
            if(!strokes.isEmpty()) {strokes.removeLast();recognized.removeLast();}
            active.clear();dragging=false;feedback="Последняя руна стёрта.";feedbackColor=MUTED;return true;
        }
        if(key.key()==GLFW.GLFW_KEY_R) {
            strokes.clear();recognized.clear();active.clear();dragging=false;
            feedback="Лист очищен.";feedbackColor=MUTED;return true;
        }
        return super.keyPressed(key);
    }
    private void cast() {
        try {
            ClientPlayNetworking.send(new CastPayload(GlyphWire.encode(strokes)));
            onClose();
        } catch(IllegalArgumentException ex) {
            feedback="Невозможно передать письмо: слишком много точек.";
            feedbackColor=0xFFFF9292;
        }
    }
}
