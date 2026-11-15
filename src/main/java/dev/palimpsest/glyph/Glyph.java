package dev.palimpsest.glyph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Glyphs form a language: INTENT / ELEMENT / optional INFLECTION. */
public enum Glyph {
    NEEDLE(Role.INTENT, "Нить", "Стрела — в одну цель", 0xFF89D8FA,
        path(.12,.53, .82,.53, .57,.29, .82,.53, .57,.77)),
    HALO(Role.INTENT, "Ореол", "Замкнутый круг — печать", 0xFF89D8FA,
        circle(.50,.50,.38,36)),
    RIPPLE(Role.INTENT, "Волна", "Буква W — импульс вокруг", 0xFF89D8FA,
        path(.12,.22, .27,.80, .46,.28, .64,.80, .87,.20)),

    CINDER(Role.ELEMENT, "Уголь", "Два языка пламени", 0xFFFF995C,
        path(.15,.85, .34,.18, .53,.70, .72,.15, .88,.84)),
    BLOOM(Role.ELEMENT, "Прорастание", "Изогнутая S", 0xFF8ED7A7,
        path(.80,.22, .60,.12, .31,.22, .19,.42, .52,.51, .79,.61, .67,.82, .37,.90, .17,.79)),
    RIME(Role.ELEMENT, "Иней", "Зазубренный кристалл", 0xFFA3DFFD,
        path(.46,.07, .52,.40, .75,.24, .60,.51, .86,.70, .53,.62, .49,.93, .40,.60, .14,.72, .39,.48, .25,.23, .46,.40, .46,.07)),
    VEIL(Role.ELEMENT, "Завеса", "Спираль к центру", 0xFFC7A4FE,
        spiral()),

    ECHO(Role.INFLECTION, "Отголосок", "Восьмёрка — повтор", 0xFFFFDB88,
        figureEight()),
    INVERT(Role.INFLECTION, "Отражение", "Песочные часы — инверсия", 0xFFFFDB88,
        path(.20,.16, .80,.16, .20,.84, .80,.84, .20,.16)),
    BLOOD(Role.INFLECTION, "Цена", "Молния — сверхзаряд", 0xFFFFDB88,
        path(.70,.08, .28,.49, .62,.49, .33,.91));

    public enum Role { INTENT, ELEMENT, INFLECTION }

    private final Role role;
    private final String display;
    private final String hint;
    private final int color;
    private final List<Point> template;

    Glyph(Role role, String display, String hint, int color, List<Point> template) {
        this.role = role;
        this.display = display;
        this.hint = hint;
        this.color = color;
        this.template = template;
    }
    public Role role() { return role; }
    public String display() { return display; }
    public String hint() { return hint; }
    public int color() { return color; }
    public List<Point> template() { return template; }
    public static List<Glyph> ofRole(Role role) {
        return Arrays.stream(values()).filter(g -> g.role == role).toList();
    }

    private static List<Point> path(double... coords) {
        List<Point> points = new ArrayList<>();
        for (int i = 0; i < coords.length; i += 2) points.add(new Point(coords[i], coords[i+1]));
        return List.copyOf(points);
    }
    private static List<Point> circle(double cx,double cy,double radius,int n) {
        List<Point> points = new ArrayList<>();
        for (int i = 0; i <= n; i++) {
            double t = (-Math.PI / 2) + (i * Math.PI * 2 / n);
            points.add(new Point(cx + radius * Math.cos(t), cy + radius * Math.sin(t)));
        }
        return List.copyOf(points);
    }
    private static List<Point> spiral() {
        List<Point> points = new ArrayList<>();
        for (int i = 0; i <= 52; i++) {
            double t = i / 52.0, angle = -Math.PI / 2 + t * 3.8 * Math.PI;
            double radius = .40 * (1 - .91*t);
            points.add(new Point(.50 + radius*Math.cos(angle), .50 + radius*Math.sin(angle)));
        }
        return List.copyOf(points);
    }
    private static List<Point> figureEight() {
        List<Point> points = new ArrayList<>();
        for (int i = 0; i <= 56; i++) {
            double t = i * Math.PI * 2 / 56;
            points.add(new Point(.5 + .37*Math.sin(t), .5 + .32*Math.sin(2*t)));
        }
        return List.copyOf(points);
    }
}
