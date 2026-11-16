package dev.palimpsest.glyph;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Run without Fabric, Gradle, Minecraft or JUnit: `bash test-recognizer.sh` */
public final class RecognizerSelfTest {
    public static void main(String[] args) {
        int passed=0;
        Random random=new Random(2026);
        for(Glyph g:Glyph.values()) {
            for(int trial=0;trial<60;trial++) {
                double rotation=Math.toRadians(random.nextDouble(-13,13));
                double scale=random.nextDouble(.45,.70);
                double jitter=random.nextDouble(.0,.009);
                List<Point> raw=alter(g.template(), rotation, scale, jitter, random);
                GlyphRecognizer.Match match=GlyphRecognizer.recognize(raw,g.role());
                if(!match.accepted() || match.glyph()!=g)
                    throw new AssertionError(g+" trial="+trial+" recognized="+match);
                passed++;
            }
        }
        if(Glyph.values().length<25)throw new AssertionError("Expected at least 25 independent glyphs");
        int correctlyRejected=0;
        for(Glyph.Role role:Glyph.Role.values()) {
            for(int k=0;k<300;k++) {
                List<Point> scribble=new ArrayList<>();
                double x=.5,y=.5;
                for(int j=0;j<42;j++) {
                    x=Math.clamp(x+random.nextGaussian()*.07,0,1);
                    y=Math.clamp(y+random.nextGaussian()*.07,0,1);
                    scribble.add(new Point(x,y));
                }
                if(GlyphRecognizer.validStroke(scribble)) {
                    if(GlyphRecognizer.recognize(scribble,role).accepted())
                        throw new AssertionError("Noise recognized as a real glyph, role="+role);
                    correctlyRejected++;
                }
            }
        }
        List<Point> bad=List.of(new Point(0,0),new Point(1,1));
        if(GlyphRecognizer.validStroke(bad)) throw new AssertionError("Short stroke accepted");
        List<List<Point>> x=List.of(alter(Glyph.NEEDLE.template(),0,.6,0,random),alter(Glyph.CINDER.template(),0,.6,0,random));
        var encoded=GlyphWire.encode(x);
        var decoded=GlyphWire.decode(encoded);
        if(decoded.size()!=2 || GlyphRecognizer.recognize(decoded.get(0),Glyph.Role.INTENT).glyph()!=Glyph.NEEDLE)
            throw new AssertionError("Wire round trip failed");
        boolean rejected=false;
        try { GlyphWire.decode("9999,9999;0,0|0,0"); } catch (IllegalArgumentException ex) {rejected=true;}
        if(!rejected) throw new AssertionError("Invalid packet accepted");
        System.out.println("PASS: "+passed+" varied real-glyph tests, "+correctlyRejected+
            " noise rejection tests, packet round-trip, invalid inputs");
    }
    private static List<Point> alter(List<Point> template, double angle, double scale, double noise, Random rng) {
        // Sample source stroke at uniform arc-length, then perturb as mouse input.
        List<Point> result=new ArrayList<>();
        double total=0;
        for(int i=1;i<template.size();i++) total+=template.get(i-1).distance(template.get(i));
        double spacing=total/78;
        double walked=0;
        Point before=template.get(0);
        for(int segment=1;segment<template.size();segment++) {
            Point after=template.get(segment);
            double length=before.distance(after);
            int n=Math.max(1,(int)Math.ceil(length/spacing));
            for(int j=0;j<n;j++) {
                double t=j/(double)n;
                double x=before.x()+(after.x()-before.x())*t-.5;
                double y=before.y()+(after.y()-before.y())*t-.5;
                double a=.5 + scale*(Math.cos(angle)*x-Math.sin(angle)*y)+rng.nextGaussian()*noise;
                double b=.5 + scale*(Math.sin(angle)*x+Math.cos(angle)*y)+rng.nextGaussian()*noise;
                result.add(new Point(Math.max(0,Math.min(1,a)),Math.max(0,Math.min(1,b))));
            }
            before=after;
        }
        Point last=template.getLast();
        result.add(new Point(.5+scale*(last.x()-.5),.5+scale*(last.y()-.5)));
        // Respect packet point budget.
        while(result.size()>GlyphRecognizer.MAX_POINTS) {
            List<Point> subset=new ArrayList<>();
            for(int i=0;i<result.size();i+=2) subset.add(result.get(i));
            result=subset;
        }
        return result;
    }
}
