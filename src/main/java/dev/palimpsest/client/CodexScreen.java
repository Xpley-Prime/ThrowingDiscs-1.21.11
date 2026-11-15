package dev.palimpsest.client;

import dev.palimpsest.content.ArcaneRituals;
import dev.palimpsest.content.ArcaneStations;
import dev.palimpsest.content.ArcaneTools;
import dev.palimpsest.glyph.Glyph;
import dev.palimpsest.glyph.Point;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;

/**
 * The Index of the World. In-game guide, not a spell picker: symbols must still be drawn.
 * Pages resize to the current Minecraft GUI scale; none of the descriptions overflow.
 */
public final class CodexScreen extends Screen {
    private static final int BG=0xEE111729, PANEL=0xED1C263F, GLOW=0xFF90DAFF;
    private static final int GOLD=0xFFF2D09B, MUTED=0xFF9CA4BB;
    private static final String[] TABS={"РУНЫ","ИНСТРУМЕНТЫ","СООРУЖЕНИЯ","РИТУАЛЫ"};
    private int tab,page,selected;
    public CodexScreen() {super(Component.literal("Книга чернил"));}

    private record Entry(String name,String detail) {}
    private List<Entry> entries() {
        List<Entry> result=new ArrayList<>();
        switch(tab) {
            case 0 -> {
                for(Glyph g:Glyph.values())result.add(new Entry(g.display(),g.hint()));
            }
            case 1 -> {
                for(ArcaneTools.Tool t:ArcaneTools.Tool.values())
                    result.add(new Entry(t.title,t.description+" · ПКМ с артефактом в руке"));
            }
            case 2 -> {
                for(ArcaneStations.Station s:ArcaneStations.Station.values())
                    result.add(new Entry(s.title,s.description+" · установи блок и нажми ПКМ"));
            }
            case 3 -> {
                for(ArcaneRituals.Ritual r:ArcaneRituals.Ritual.values()) {
                    String catalyst=BuiltInRegistries.ITEM.getKey(r.catalyst).toString();
                    result.add(new Entry(r.name,r.description+" · катализатор: "+catalyst));
                }
            }
        }
        return result;
    }
    private String fit(String t,int max) {
        if(max<15)return "";
        if(font.width(t)<=max)return t;
        while(!t.isEmpty() && font.width(t+"…")>max)t=t.substring(0,t.length()-1);
        return t+"…";
    }
    private int rows(){return Math.max(3,Math.min(10,(height-142)/25));}
    private int countPages(){return Math.max(1,(int)Math.ceil(entries().size()/(double)rows()));}
    private void page(int diff){
        page=Math.max(0,Math.min(countPages()-1,page+diff));
        selected=page*rows();
    }
    private void tab(int next){tab=Math.floorMod(next,4);page=0;selected=0;}

    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float delta) {
        g.fill(0,0,width,height,BG);
        g.drawCenteredString(font,fit("✦ АРХИВ ЧЕРНИЛ · КНИГА МИРА ✦",width-20),width/2,13,GOLD);
        g.drawCenteredString(font,fit("100 механик · 10 начертаний · 48 формул",width-20),
            width/2,28,MUTED);
        int left=12,right=width-12,top=54,bottom=height-49;
        if(right-left<90||bottom-top<70)return;
        g.fill(left,top,right,bottom,PANEL);
        int tabW=Math.max(1,(right-left)/4);
        for(int i=0;i<4;i++){
            int x=left+i*tabW;
            g.fill(x,top,x+tabW-1,top+24,i==tab?0xFF485473:0xFF222B43);
            g.drawCenteredString(font,fit(TABS[i],tabW-8),x+tabW/2,top+8,i==tab?GOLD:MUTED);
        }
        List<Entry> records=entries();
        int rowCount=rows();
        int listX=left+8;
        int listW=width>=570?Math.min(280,(right-left)*43/100):right-left-16;
        int detailsX=listX+listW+12;
        boolean sideBySide=right-detailsX>=125;
        for(int i=0;i<rowCount;i++){
            int index=page*rowCount+i;
            if(index>=records.size())break;
            int yy=top+34+i*25;
            if(yy+22>=bottom-6)break;
            g.fill(listX,yy,listX+listW,yy+23,index==selected?0xFF394A68:0xFF222D44);
            String entry=(index+1)+". "+records.get(index).name();
            g.drawString(font,fit(entry,listW-12),listX+6,yy+7,index==selected?GOLD:GLOW,false);
        }
        if(sideBySide && selected<records.size()){
            Entry e=records.get(selected);
            g.drawString(font,fit(e.name(),right-detailsX-10),detailsX,top+39,GOLD,false);
            drawWrapped(g,e.detail(),detailsX,top+59,right-detailsX-8,5,MUTED);
            if(tab==0){
                Glyph glyph=Glyph.values()[selected];
                int glyphSize=Math.min(130,Math.min(right-detailsX-16,bottom-(top+128)));
                if(glyphSize>=44) {
                    int gx=detailsX+Math.max(0,(right-detailsX-glyphSize)/2),gy=top+115;
                    g.fill(gx-4,gy-4,gx+glyphSize+4,gy+glyphSize+4,0xF0101528);
                    drawStroke(g,glyph.template(),gx,gy,glyphSize,glyph.color());
                }
            } else if(tab==3){
                g.drawString(font,fit("Алтарь + катализатор",right-detailsX-8),detailsX,top+106,GLOW);
            } else {
                g.drawString(font,fit("Расходует магические чернила",right-detailsX-8),
                    detailsX,top+106,GLOW);
            }
        }
        String pages="←  Страница "+(page+1)+" / "+countPages()+"  →";
        g.drawCenteredString(font,fit(pages,width-24),width/2,height-37,GOLD);
        g.drawCenteredString(font,fit("← → листать    1–4 разделы    Esc закрыть",width-20),
            width/2,height-19,MUTED);
        super.render(g,mouseX,mouseY,delta);
    }
    private void drawWrapped(GuiGraphics g,String line,int x,int y,int maxWidth,int maxLines,int color) {
        String[] words=line.split(" ");
        String acc="";int printed=0;
        for(String word:words) {
            String s=acc.isEmpty()?word:acc+" "+word;
            if(font.width(s)>maxWidth&&!acc.isEmpty()){
                g.drawString(font,fit(acc,maxWidth),x,y+printed*14,color);
                printed++;acc=word;
                if(printed>=maxLines)return;
            }else acc=s;
        }
        if(!acc.isEmpty()&&printed<maxLines)g.drawString(font,fit(acc,maxWidth),x,y+printed*14,color);
    }
    private void drawStroke(GuiGraphics g,List<Point> pts,int x,int y,int size,int color) {
        for(int i=1;i<pts.size();i++){
            Point a=pts.get(i-1),b=pts.get(i);
            double ax=x+a.x()*size,ay=y+a.y()*size,bx=x+b.x()*size,by=y+b.y()*size;
            int steps=Math.max(1,(int)Math.ceil(Math.hypot(bx-ax,by-ay)));
            for(int k=0;k<=steps;k++){
                double t=k/(double)steps;
                int px=(int)Math.round(ax+(bx-ax)*t),py=(int)Math.round(ay+(by-ay)*t);
                g.fill(px-1,py-1,px+2,py+2,color);
            }
        }
        Point origin=pts.getFirst();
        int px=(int)(x+origin.x()*size),py=(int)(y+origin.y()*size);
        g.fill(px-2,py-2,px+3,py+3,0xFFFFDC78);
    }
    @Override public boolean keyPressed(KeyEvent key){
        switch(key.key()){
            case GLFW.GLFW_KEY_RIGHT,GLFW.GLFW_KEY_PAGE_DOWN -> {page(1);return true;}
            case GLFW.GLFW_KEY_LEFT,GLFW.GLFW_KEY_PAGE_UP -> {page(-1);return true;}
            case GLFW.GLFW_KEY_1 -> {tab(0);return true;}
            case GLFW.GLFW_KEY_2 -> {tab(1);return true;}
            case GLFW.GLFW_KEY_3 -> {tab(2);return true;}
            case GLFW.GLFW_KEY_4 -> {tab(3);return true;}
        }
        return super.keyPressed(key);
    }
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean dblClick) {
        if(e.button()!=GLFW.GLFW_MOUSE_BUTTON_LEFT)return super.mouseClicked(e,dblClick);
        int left=12,right=width-12,top=54,tabW=Math.max(1,(right-left)/4);
        if(e.y()>=top&&e.y()<=top+24){tab(Math.max(0,Math.min(3,((int)e.x()-left)/tabW)));return true;}
        if(e.y()>=height-48){
            if(e.x()<width/2)page(-1);else page(1);
            return true;
        }
        int listX=left+8,listW=width>=570?Math.min(280,(right-left)*43/100):right-left-16;
        if(e.x()>=listX && e.x()<listX+listW && e.y()>top+34){
            int index=page*rows()+((int)e.y()-(top+34))/25;
            if(index>=0&&index<entries().size())selected=index;
            return true;
        }
        return super.mouseClicked(e,dblClick);
    }
    @Override public boolean isPauseScreen(){return false;}
}
