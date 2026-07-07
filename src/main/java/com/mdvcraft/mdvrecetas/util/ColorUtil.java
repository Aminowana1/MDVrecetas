package com.mdvcraft.mdvrecetas.util;

import org.bukkit.ChatColor;

public final class ColorUtil {
    private ColorUtil() {
    }

    public static String color(String input) {
        if (input == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    public static String stripColor(String input) {
        return ChatColor.stripColor(color(input));
    }
}
