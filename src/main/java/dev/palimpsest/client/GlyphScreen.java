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

    public GlyphScreen() {super(Component.literal("Палимпсест · Письмена"));}
    @Override protected void init() {
        canvasSize=Math.max(100,Math.min(260,Math.min(width-62,height-135)));
        canvasX=(width-canvasSize)/2;
        canvasY=Math.max(54,(height-canvasSize)/2);
    }
    @Override public boolean isPauseScreen() {return false;}

    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float delta) {
        // The transparent veil keeps the actual Minecraft world visible behind the ink.
        g.fill(0,0,width,height,0xB0090C18);
        int cx=canvasX+canvasSize/2, cy=canvasY+canvasSize/2;
        g.drawCenteredString(font,"✦  PALIMPSEST  ·  ЧЕРНИЛА МИРА  ✦",width/2,16,0xFFF0D6A6);
        g.drawCenteredString(font,"Перо связывает начертания с реальностью",width/2,31,MUTED);
        g.fill(canvasX-9,canvasY-9,canvasX+canvasSize+9,canvasY+canvasSize+9,0xB1151A30);
        g.fill(canvasX-2,canvasY-2,canvasX+canvasSize+2,canvasY+canvasSize+2,FRAME);
        g.fill(canvasX,canvasY,canvasX+canvasSize,canvasY+canvasSize,BACK);
        for(int i=1;i<8;i++) {
            int x=canvasX+canvasSize*i/8, y=canvasY+canvasSize*i/8;
            g.fill(x,canvasY,x+1,canvasY+canvasSize,0x2E8497B7);
            g.fill(canvasX,y,canvasX+canvasSize,y+1,0x2E8497B7);
        }
        // Decorative compass — ink is a gesture, not a preset button.
        g.fill(cx-1,canvasY+8,cx+1,canvasY+17,0x9995BCE7);
        g.fill(cx-1,canvasY+canvasSize-17,cx+1,canvasY+canvasSize-8,0x9995BCE7);
        g.fill(canvasX+8,cy-1,canvasX+17,cy+1,0x9995BCE7);
        g.fill(canvasX+canvasSize-17,cy-1,canvasX+canvasSize-8,cy+1,0x9995BCE7);

        if(active.size()>1) renderPath(g,active,canvasX,canvasY,canvasSize,0xFFC3F4FF,3);
        if(!dragging && active.isEmpty()) {
            g.drawCenteredString(font,"✧",cx,cy-12,0x6684BCE6);
            g.drawCenteredString(font,"НАЧЕРТАЙ СИМВОЛ",cx,cy+2,0x667DB6DD);
        }
        g.drawCenteredString(font,feedback,width/2,canvasY+canvasSize+14,feedbackColor);
        g.drawCenteredString(font,"Enter — исполнить    Backspace — назад    R — стереть    Esc — закрыть",
            width/2,height-20,0xFFCBD1DC);
        g.drawString(font,"§bЧЕРНИЛА §f"+PalimpsestClient.mana+" / 100",11,12,0xFFFFFFFF,true);

        for(int i=0;i<3;i++) {
            int bx=canvasX+i*(canvasSize/3),by=canvasY-25;
            Glyph.Role role=roleFor(i);
            String label=(i+1)+" "+ switch(role){case INTENT->"НАМЕРЕНИЕ";case ELEMENT->"СТИХИЯ";case INFLECTION->"ОТТЕНОК";};
            int color= i<recognized.size() ? recognized.get(i).glyph().color() : i==strokes.size()? GLOW:MUTED;
            g.drawString(font,label,bx,by,color,false);
        }
        if(width>=canvasSize+320) drawLexicon(g,canvasX+canvasSize+24,canvasY-4);
        if(strokes.size()>0) drawFormula(g,canvasX-134,canvasY);
        super.render(g,mouseX,mouseY,delta);
    }

    private void drawFormula(GuiGraphics g,int x,int y) {
        if(x<8)return;
        g.drawString(font,"ЗАПИСАНО",x,y,0xFFE2C998,true);
        for(int i=0;i<recognized.size();i++) {
            Glyph glyph=recognized.get(i).glyph();
            g.drawString(font,(i+1)+". "+glyph.display(),x,y+18+i*30,glyph.color(),true);
            g.drawString(font,(int)Math.round(100*recognized.get(i).confidence())+"% сходство",
                x+13,y+29+i*30,MUTED,false);
        }
        if(strokes.size()>=2) g.drawString(font,"↵ Enter: сотворить",x,y+135,0xFFB6E7B4,false);
    }

    private void drawLexicon(GuiGraphics g,int x,int y) {
        Glyph.Role role=roleFor(Math.min(2,strokes.size()));
        g.drawString(font,"АТЛАС НАЧЕРТАНИЙ",x,y,0xFFF0D6A6,true);
        g.drawString(font,"Образцы · веди от золотой точки",x,y+13,MUTED,false);
        List<Glyph> glyphs=Glyph.ofRole(role);
        for(int i=0;i<glyphs.size();i++) {
            Glyph glyph=glyphs.get(i);
            int yy=y+35+i*53;
            g.fill(x,yy,x+47,yy+43,0xAA1C1D32);
            renderPath(g,glyph.template(),x+4,yy+4,37,glyph.color(),1);
            Point origin=glyph.template().getFirst();
            int sx=(int)(x+4+origin.x()*37),sy=(int)(yy+4+origin.y()*37);
            g.fill(sx-2,sy-2,sx+3,sy+3,0xFFFFD56B);
            g.drawString(font,glyph.display(),x+54,yy+7,glyph.color(),true);
            g.drawString(font,glyph.hint(),x+54,yy+21,MUTED,false);
        }
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
