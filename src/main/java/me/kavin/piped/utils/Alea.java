package me.kavin.piped.utils;

public class Alea {

    private static final double NORM32 = 2.3283064365386963e-10; // 2^-32
    private double s0, s1, s2;
    private int c = 1;

    public double next() {
        double t = 2091639.0 * s0 + c * NORM32; // 2^-32
        s0 = s1;
        s1 = s2;
        return s2 = t - (c = (int) t);
    }

    public Alea(String seed) {
        s0 = mash(" ");
        s1 = mash(" ");
        s2 = mash(" ");

        s0 -= mash(seed);

        if (s0 < 0)
            s0 += 1;
        s1 -= mash(seed);
        if (s1 < 0)
            s1 += 1;
        s2 -= mash(seed);
        if (s2 < 0)
            s2 += 1;
    }

    private long n = 0xefc8249dL;

    public double mash(String x) {
        double h;

        for (char c : x.toCharArray()) {
            n += c;
            h = 0.02519603282416938 * n;
            n = (long) h;
            h -= n;
            h *= n;
            n = (long) h;
            h -= n;
            n += h * 0x100000000L;
        }
        return n * 2.3283064365386963e-10; // 2^-32
    }
}
