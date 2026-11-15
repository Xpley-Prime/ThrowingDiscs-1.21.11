package dev.palimpsest.glyph;

import java.util.ArrayList;
import java.util.List;

/** Bounded protocol for stroke-only transmissions; no trusted glyph identifiers. */
public final class GlyphWire {
    public static final int MAX_PACKET_LENGTH = 3500;
    private GlyphWire() {}

    public static String encode(List<List<Point>> glyphs) {
        if (glyphs.size()<2 || glyphs.size()>3) throw new IllegalArgumentException("2–3 strokes required");
        StringBuilder s=new StringBuilder();
        for (List<Point> points : glyphs) {
            if (!GlyphRecognizer.validStroke(points)) throw new IllegalArgumentException("Invalid stroke");
            if (!s.isEmpty()) s.append('|');
            for (int i=0;i<points.size();i++) {
                Point p=points.get(i);
                if (i>0) s.append(';');
                s.append((int)Math.round(p.x()*1000)).append(',').append((int)Math.round(p.y()*1000));
            }
        }
        if (s.length()>MAX_PACKET_LENGTH) throw new IllegalArgumentException("Too many points");
        return s.toString();
    }

    public static List<List<Point>> decode(String data) {
        if (data==null || data.length()>MAX_PACKET_LENGTH || data.isEmpty())
            throw new IllegalArgumentException("Malformed packet length");
        String[] strokes=data.split("\\|",-1);
        if (strokes.length<2 || strokes.length>3) throw new IllegalArgumentException("Expected 2–3 glyphs");
        List<List<Point>> result=new ArrayList<>();
        for (String s:strokes) {
            String[] pieces=s.split(";",-1);
            if (pieces.length<6 || pieces.length>GlyphRecognizer.MAX_POINTS)
                throw new IllegalArgumentException("Bad stroke count");
            List<Point> points=new ArrayList<>();
            for(String piece:pieces) {
                String[] xy=piece.split(",",-1);
                if(xy.length!=2 || xy[0].length()>4 || xy[1].length()>4)
                    throw new IllegalArgumentException("Bad coordinate format");
                int x=Integer.parseInt(xy[0]), y=Integer.parseInt(xy[1]);
                if(x<0 || x>1000 || y<0 || y>1000) throw new IllegalArgumentException("Out of bounds");
                points.add(new Point(x/1000.0,y/1000.0));
            }
            if(!GlyphRecognizer.validStroke(points)) throw new IllegalArgumentException("Invalid geometry");
            result.add(List.copyOf(points));
        }
        return List.copyOf(result);
    }
}
