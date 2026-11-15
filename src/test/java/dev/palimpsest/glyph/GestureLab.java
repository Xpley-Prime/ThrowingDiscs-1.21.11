package dev.palimpsest.glyph;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

/** A self-contained handwriting laboratory: no Fabric/Minecraft downloads needed. */
public final class GestureLab extends JPanel {
    private static final int SIZE = 320;
    private final List<List<Point>> strokes = new ArrayList<>();
    private final List<Glyph> glyphs = new ArrayList<>();
    private final List<Point> line = new ArrayList<>();
    private String status = "Удерживай ЛКМ в квадрате и рисуй";
    private boolean drawing;

    public GestureLab() {
        setPreferredSize(new Dimension(690,460));
        setBackground(new Color(16,19,34));
        var mouse=new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (strokes.size()<3 && inCanvas(e.getX(),e.getY())) {
                    drawing=true;line.clear();point(e);
                }
            }
            @Override public void mouseDragged(MouseEvent e) { if(drawing){point(e);repaint();} }
            @Override public void mouseReleased(MouseEvent e) {
                if(!drawing)return;
                drawing=false;point(e);
                var match=GlyphRecognizer.recognize(line,role());
                if(match.accepted()) {
                    strokes.add(List.copyOf(line));glyphs.add(match.glyph());
                    status=match.glyph().display()+" · "+Math.round(match.confidence()*100)+"%";
                } else status="Не распознано. Увеличь рисунок или повтори";
                line.clear();repaint();
            }
        };
        addMouseListener(mouse);addMouseMotionListener(mouse);
        setFocusable(true);
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("BACK_SPACE"),"undo");
        getActionMap().put("undo",new AbstractAction(){
            @Override public void actionPerformed(java.awt.event.ActionEvent e){
                if(!strokes.isEmpty()){strokes.removeLast();glyphs.removeLast();}
                status="Предыдущая руна удалена";repaint();
            }
        });
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("R"),"reset");
        getActionMap().put("reset",new AbstractAction(){
            @Override public void actionPerformed(java.awt.event.ActionEvent e){
                strokes.clear();glyphs.clear();line.clear();status="Лист очищен";repaint();
            }
        });
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ENTER"),"spell");
        getActionMap().put("spell",new AbstractAction(){
            @Override public void actionPerformed(java.awt.event.ActionEvent e){
                if(glyphs.size()<2)status="Нужны минимум две руны";
                else status="ФОРМУЛА: "+glyphs.stream().map(Glyph::display).reduce((a,b)->a+" → "+b).orElse("");
                repaint();
            }
        });
    }
    private boolean inCanvas(int x,int y) {return x>=30&&y>=70&&x<=30+SIZE&&y<=70+SIZE;}
    private Glyph.Role role(){return strokes.size()==0?Glyph.Role.INTENT:strokes.size()==1?Glyph.Role.ELEMENT:Glyph.Role.INFLECTION;}
    private void point(MouseEvent e){
        double x=Math.clamp((e.getX()-30.0)/SIZE,0,1),y=Math.clamp((e.getY()-70.0)/SIZE,0,1);
        Point p=new Point(x,y);
        if(!line.isEmpty() && p.distance(line.getLast())<.004)return;
        line.add(p);
        if(line.size()>GlyphRecognizer.MAX_POINTS){
            var reduced=new ArrayList<Point>();for(int i=0;i<line.size();i+=2)reduced.add(line.get(i));
            line.clear();line.addAll(reduced);
        }
    }
    @Override protected void paintComponent(Graphics base){
        super.paintComponent(base);
        Graphics2D g=(Graphics2D)base.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(238,207,148));g.setFont(new Font("SansSerif",Font.BOLD,19));
        g.drawString("PALIMPSEST · лаборатория жестов",30,32);
        g.setFont(new Font("SansSerif",Font.PLAIN,12));g.setColor(new Color(160,166,196));
        g.drawString("Это тест распознавателя без Minecraft. Enter · формула  |  Backspace · назад  |  R · очистить",30,51);
        g.setColor(new Color(28,31,51));g.fillRoundRect(30,70,SIZE,SIZE,8,8);
        g.setColor(new Color(63,69,100));
        for(int i=1;i<8;i++) {
            int p=30+i*SIZE/8;g.drawLine(p,70,p,390);
            int v=70+i*SIZE/8;g.drawLine(30,v,350,v);
        }
        draw(g,line,30,70,SIZE,new Color(188,241,255),3);
        g.setColor(new Color(238,207,148));g.setFont(new Font("SansSerif",Font.BOLD,14));
        g.drawString("Шаг "+Math.min(strokes.size()+1,3)+" / 3 · "+role(),385,85);
        g.setFont(new Font("SansSerif",Font.PLAIN,11));
        g.setColor(new Color(173,183,208));g.drawString("Обведи любой образец одним штрихом",385,101);
        List<Glyph> possible=Glyph.ofRole(role());
        for(int i=0;i<possible.size();i++){
            Glyph glyph=possible.get(i);int y=120+i*62;
            draw(g,glyph.template(),395,y,51,new Color(glyph.color(),true),2);
            Point origin=glyph.template().getFirst();
            g.setColor(new Color(252,214,101));g.fillOval((int)(395+origin.x()*51)-3,(int)(y+origin.y()*51)-3,6,6);
            g.setColor(new Color(228,223,246));g.setFont(new Font("SansSerif",Font.BOLD,12));g.drawString(glyph.display(),455,y+17);
            g.setFont(new Font("SansSerif",Font.PLAIN,11));g.setColor(new Color(164,169,189));g.drawString(glyph.hint(),455,y+33);
        }
        g.setFont(new Font("SansSerif",Font.PLAIN,13));g.setColor(new Color(194,226,246));
        for(int i=0;i<glyphs.size();i++)g.drawString((i+1)+". "+glyphs.get(i).display(),30+i*125,411);
        g.setColor(new Color(250,221,162));g.drawString(status,30,443);
        g.dispose();
    }
    private static void draw(Graphics2D g,List<Point> points,int x,int y,int size,Color color,int width){
        if(points.size()<2)return;
        Path2D.Double path=new Path2D.Double();
        path.moveTo(x+points.getFirst().x()*size,y+points.getFirst().y()*size);
        for(int i=1;i<points.size();i++)path.lineTo(x+points.get(i).x()*size,y+points.get(i).y()*size);
        g.setColor(color);g.setStroke(new BasicStroke(width,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.draw(path);
    }
    public static void main(String[] args){
        SwingUtilities.invokeLater(()->{
            JFrame frame=new JFrame("Palimpsest · Gesture Lab");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(new GestureLab());frame.pack();frame.setResizable(false);
            frame.setLocationRelativeTo(null);frame.setVisible(true);
        });
    }
}
