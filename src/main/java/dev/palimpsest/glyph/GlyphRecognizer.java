package dev.palimpsest.glyph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Rotation-tolerant, scale-invariant, one-stroke gesture recognizer.
 * Every glyph is reclassified on the dedicated server; clients only transmit raw points.
 * Stroke *direction* and order intentionally matter (it's a handwritten language).
 */
public final class GlyphRecognizer {
    public static final int MAX_POINTS = 120;
    private static final int SAMPLES = 64;
    private static final double MAX_DISTANCE = .42;
    private GlyphRecognizer() {}

    public record Match(Glyph glyph, double confidence) {
        public boolean accepted() { return glyph != null && confidence >= .60; }
    }

    public static Match recognize(List<Point> raw, Glyph.Role role) {
        if (!validStroke(raw)) return new Match(null, 0);
        List<Point> input = prepare(raw);
        return Glyph.ofRole(role).stream()
            .map(g -> new Match(g, 1 - Math.min(1, bestDistance(input, prepare(g.template())) / MAX_DISTANCE)))
            .max(Comparator.comparingDouble(Match::confidence))
            .orElse(new Match(null, 0));
    }

    /** Defensive size, finite-value and shape limits shared by client and server. */
    public static boolean validStroke(List<Point> raw) {
        if (raw == null || raw.size() < 6 || raw.size() > MAX_POINTS) return false;
        double minX=Double.POSITIVE_INFINITY, minY=minX;
        double maxX=Double.NEGATIVE_INFINITY, maxY=maxX, distance=0;
        Point before = null;
        for (Point p : raw) {
            if (p == null || !Double.isFinite(p.x()) || !Double.isFinite(p.y()) ||
                p.x() < 0 || p.x() > 1 || p.y() < 0 || p.y() > 1) return false;
            minX = Math.min(minX, p.x()); maxX = Math.max(maxX, p.x());
            minY = Math.min(minY, p.y()); maxY = Math.max(maxY, p.y());
            if (before != null) distance += before.distance(p);
            before = p;
        }
        return Math.max(maxX-minX, maxY-minY) >= .25 && distance >= .4 && distance <= 15;
    }

    /** Evenly resample polyline, preserve aspect ratio, center around origin. */
    static List<Point> prepare(List<Point> input) {
        List<Point> samples = resample(input, SAMPLES);
        double minX=Double.POSITIVE_INFINITY, minY=minX;
        double maxX=Double.NEGATIVE_INFINITY, maxY=maxX;
        for (Point p : samples) {
            minX=Math.min(minX,p.x()); maxX=Math.max(maxX,p.x());
            minY=Math.min(minY,p.y()); maxY=Math.max(maxY,p.y());
        }
        double extent = Math.max(1e-9, Math.max(maxX-minX, maxY-minY));
        double cx=0,cy=0;
        for (Point p : samples) { cx+=p.x(); cy+=p.y(); }
        cx/=samples.size(); cy/=samples.size();
        List<Point> out = new ArrayList<>(samples.size());
        for (Point p : samples) out.add(new Point((p.x()-cx)/extent, (p.y()-cy)/extent));
        return out;
    }

    private static List<Point> resample(List<Point> in, int count) {
        double[] cumulative = new double[in.size()];
        for (int i=1;i<in.size();i++) cumulative[i]=cumulative[i-1]+in.get(i).distance(in.get(i-1));
        double total=cumulative[in.size()-1];
        if (total < 1e-9) return java.util.Collections.nCopies(count, in.get(0));
        List<Point> out=new ArrayList<>(count);
        int edge=1;
        for (int i=0;i<count;i++) {
            double target=i*total/(count-1);
            while (edge<in.size()-1 && cumulative[edge]<target) edge++;
            double length=cumulative[edge]-cumulative[edge-1];
            double part=length < 1e-9 ? 0 : (target-cumulative[edge-1])/length;
            Point a=in.get(edge-1), b=in.get(edge);
            out.add(new Point(a.x()+(b.x()-a.x())*part, a.y()+(b.y()-a.y())*part));
        }
        return out;
    }

    private static double bestDistance(List<Point> a, List<Point> b) {
        double best=Double.POSITIVE_INFINITY;
        // Closed glyphs may start anywhere along the circumference.
        boolean closed = a.getFirst().distance(a.getLast())<.22 &&
                         b.getFirst().distance(b.getLast())<.1;
        int shifts = closed ? 16 : 1;
        for (int d=-16; d<=16; d+=4) {
            double angle=Math.toRadians(d), cos=Math.cos(angle), sin=Math.sin(angle);
            for (int shift=0;shift<shifts;shift++) {
                double total=0;
                for (int i=0;i<SAMPLES;i++) {
                    Point p=a.get(i), q=b.get((i+shift*4)%SAMPLES);
                    double x=p.x()*cos-p.y()*sin, y=p.x()*sin+p.y()*cos;
                    total+=Math.hypot(x-q.x(),y-q.y());
                }
                best=Math.min(best,total/SAMPLES);
            }
        }
        return best;
    }
}
