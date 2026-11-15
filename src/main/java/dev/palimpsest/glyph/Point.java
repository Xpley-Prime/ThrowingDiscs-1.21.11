package dev.palimpsest.glyph;

/** A normalized pen coordinate. No Minecraft or Fabric dependency. */
public record Point(double x, double y) {
    public double distance(Point p) { return Math.hypot(x - p.x, y - p.y); }
}
