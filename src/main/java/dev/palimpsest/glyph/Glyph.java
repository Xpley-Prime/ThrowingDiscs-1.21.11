package dev.palimpsest.glyph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Every glyph is one continuous, human-drawable stroke. No pen lifts,
 * teleporting segments or hidden strokes. No icon is a button to cast.
 */
public enum Glyph {
    NEEDLE(Role.INTENT, "Нить", "Стрела: точное действие в выбранной точке",0xFF7BE0FF,
        path(.13,.52,.83,.52,.60,.30,.83,.52,.60,.74)),
    HALO(Role.INTENT, "Ореол", "Круг: долговечная напольная печать",0xFF7BE0FF,
        circle(.50,.50,.37,40)),
    RIPPLE(Role.INTENT, "Волна", "W: сферический импульс из центра",0xFF7BE0FF,
        path(.13,.22,.26,.80,.45,.28,.64,.80,.86,.20)),
    FAN(Role.INTENT, "Разлом", "Арка: область изменения земли",0xFF7BE0FF,
        path(.12,.82,.18,.55,.31,.27,.50,.15,.69,.27,.82,.55,.88,.82)),
    BIND(Role.INTENT, "Плетение", "Лента: дорожка магии перед магом",0xFF7BE0FF,
        path(.17,.17,.84,.17,.22,.43,.79,.57,.17,.83,.84,.83)),
    SPIRE(Role.INTENT, "Столп", "Треугольник: вертикальный столб силы",0xFF7BE0FF,
        path(.16,.82,.52,.14,.85,.82,.16,.82)),

    CINDER(Role.ELEMENT, "Уголь", "М: жар, пламя, выплавка и возгорание",0xFFFFA35B,
        path(.15,.84,.31,.16,.51,.73,.71,.16,.85,.84)),
    BLOOM(Role.ELEMENT, "Жизнь", "S: рост, исцеление и живая земля",0xFF77E6A3,
        path(.80,.23,.59,.11,.31,.22,.18,.41,.49,.51,.81,.63,.69,.83,.37,.90,.18,.77)),
    RIME(Role.ELEMENT, "Иней", "Зигзаг Z: лёд и остановка времени воды",0xFF9BE7FF,
        path(.17,.20,.84,.20,.19,.79,.85,.79)),
    VEIL(Role.ELEMENT, "Завеса", "Спираль: невесомость, скрытие, фазы",0xFFC59EFF,
        spiral()),
    STONE(Role.ELEMENT, "Камень", "Лестница: скала и строительная магия",0xFFCDC1AA,
        path(.14,.85,.14,.63,.37,.63,.37,.41,.61,.41,.61,.16,.86,.16)),
    TIDE(Role.ELEMENT, "Прилив", "Две волны: вода, дыхание и течение",0xFF67B9FA,
        path(.10,.40,.25,.21,.43,.40,.59,.21,.78,.40,.88,.23,.76,.63,.57,.82,.40,.63,.23,.82,.10,.63)),
    STORM(Role.ELEMENT, "Гроза", "Громовая ветвь: разряд и окисление",0xFFFFE68C,
        path(.17,.24,.75,.13,.42,.46,.80,.59,.37,.88,.55,.58,.17,.55)),
    LIGHT(Role.ELEMENT, "Сияние", "Пятиконечная звезда: свет и очищение",0xFFFFE7A5,
        star()),
    SHADOW(Role.ELEMENT, "Тень", "Серп: глушение и затемнение",0xFF9C9ECF,
        path(.72,.12,.48,.14,.30,.29,.22,.49,.28,.70,.49,.83,.75,.78,.59,.64,.53,.51,.62,.35,.72,.12)),
    WARP(Role.ELEMENT, "Пространство", "Портал: перемещение и искривление пути",0xFFCE8AFF,
        path(.21,.22,.77,.22,.77,.77,.22,.77,.22,.38,.63,.38,.63,.63,.40,.63,.40,.22)),
    ROOT(Role.ELEMENT, "Корень", "U: растения, мох и обновление почвы",0xFF99D46D,
        path(.18,.18,.20,.65,.33,.83,.51,.89,.70,.79,.82,.59,.81,.17)),
    METAL(Role.ELEMENT, "Металл", "Двойной зубец: медь и рудная алхимия",0xFFDDAB7C,
        path(.16,.44,.33,.18,.51,.47,.69,.18,.86,.43,.68,.75,.47,.87,.24,.75,.16,.44)),
    WIND(Role.ELEMENT, "Ветер", "Три струи: порыв и движение",0xFFA6F5E5,
        path(.12,.23,.85,.23,.68,.41,.13,.42,.29,.61,.86,.62,.66,.82)),

    ECHO(Role.INFLECTION, "Эхо", "Восьмёрка: повторяет действие",0xFFFFD88A, figureEight()),
    INVERT(Role.INFLECTION, "Обратность", "Песочные часы: противоположный эффект",0xFFFFD88A,
        path(.18,.17,.82,.17,.20,.83,.82,.83,.18,.17)),
    BLOOD(Role.INFLECTION, "Жертва", "Молния: больше силы ценой здоровья",0xFFFFD88A,
        path(.71,.09,.28,.48,.63,.48,.34,.91)),
    FOCUS(Role.INFLECTION, "Точность", "Ромб: усиливает точечное воздействие",0xFFFFD88A,
        path(.50,.12,.86,.50,.50,.87,.14,.50,.50,.12)),
    SPREAD(Role.INFLECTION, "Широта", "Крылья: увеличивает радиус воздействия",0xFFFFD88A,
        path(.12,.19,.87,.50,.12,.82)),
    SUSTAIN(Role.INFLECTION, "Долголетие", "U с плоским дном: увеличивает длительность",0xFFFFD88A,
        path(.22,.16,.22,.83,.78,.83,.78,.16)),
    ANCHOR(Role.INFLECTION, "Якорь", "Крест: связывает силу с местом",0xFFFFD88A,
        path(.50,.11,.50,.87,.17,.59,.83,.59)),
    SILENCE(Role.INFLECTION, "Тишина", "Крест-накрест: тихая экономная магия",0xFFFFD88A,
        path(.18,.19,.81,.81,.81,.19,.18,.81));

    public enum Role {INTENT,ELEMENT,INFLECTION}
    private final Role role;
    private final String display;
    private final String hint;
    private final int color;
    private final List<Point> template;
    Glyph(Role role,String display,String hint,int color,List<Point> template){
        this.role=role;this.display=display;this.hint=hint;this.color=color;this.template=template;
    }
    public Role role(){return role;}
    public String display(){return display;}
    public String hint(){return hint;}
    public int color(){return color;}
    public List<Point> template(){return template;}
    public static List<Glyph> ofRole(Role role){
        return Arrays.stream(values()).filter(g->g.role==role).toList();
    }
    private static List<Point> path(double... c){
        List<Point> p=new ArrayList<>();
        for(int i=0;i<c.length;i+=2)p.add(new Point(c[i],c[i+1]));
        return List.copyOf(p);
    }
    private static List<Point> circle(double x,double y,double radius,int samples){
        List<Point> p=new ArrayList<>();
        for(int i=0;i<=samples;i++){
            double a=-Math.PI/2+i*Math.PI*2/samples;
            p.add(new Point(x+radius*Math.cos(a),y+radius*Math.sin(a)));
        }
        return List.copyOf(p);
    }
    private static List<Point> spiral(){
        List<Point> p=new ArrayList<>();
        for(int i=0;i<=52;i++){
            double t=i/52.0,a=-Math.PI/2+3.8*Math.PI*t,r=.4*(1-.91*t);
            p.add(new Point(.5+r*Math.cos(a),.5+r*Math.sin(a)));
        }
        return List.copyOf(p);
    }
    private static List<Point> figureEight(){
        List<Point> p=new ArrayList<>();
        for(int i=0;i<=56;i++){
            double a=i*Math.PI*2/56;
            p.add(new Point(.5+.36*Math.sin(a),.5+.32*Math.sin(2*a)));
        }
        return List.copyOf(p);
    }
    private static List<Point> star(){
        List<Point> p=new ArrayList<>();
        for(int i=0;i<=5;i++){
            int n=i*2%5;
            double a=-Math.PI/2+n*2*Math.PI/5;
            p.add(new Point(.5+.4*Math.cos(a),.5+.4*Math.sin(a)));
        }
        return List.copyOf(p);
    }
}
