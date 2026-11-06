package com.qingming.vault.util;

import org.bukkit.ChatColor;

/** Colour codes. */
public final class Text {

    private Text() {
    }

    public static String color(String s) {
        return s == null ? "" : s.replace('&', '\u00a7');
    }

    public static String plain(String s) {
        return s == null ? "" : ChatColor.stripColor(color(s));
    }

    public static String num(long n) {
        return String.format("%,d", n);
    }

    public static String shortNum(long n) {
        if (n < 10000L) {
            return String.valueOf(n);
        }
        if (n < 1000000L) {
            double v = n / 10000.0;
            return (v >= 100 ? String.valueOf((long) v) : trim(v)) + "万";
        }
        double v = n / 1000000.0;
        return (v >= 100 ? String.valueOf((long) v) : trim(v)) + "亿";
    }

    private static String trim(double v) {
        String s = String.format("%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }
}
