package com.mdvcraft.mdvrecetas.service;

import com.mdvcraft.mdvrecetas.MDVRecetasPlugin;
import com.mdvcraft.mdvrecetas.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class ForjadorXpService {
    private final MDVRecetasPlugin plugin;
    private final DecimalFormat decimalFormat;

    public ForjadorXpService(MDVRecetasPlugin plugin) {
        this.plugin = plugin;
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        this.decimalFormat = new DecimalFormat("0.##", symbols);
    }

    public void award(Player player, double amount, String recipeId) {
        if (player == null || amount <= 0 || !plugin.getConfig().getBoolean("forjador.enabled", true)) {
            return;
        }

        String xp = decimalFormat.format(amount);
        String profession = plugin.getConfig().getString("forjador.profession-id", "forjador");
        String command = plugin.getConfig().getString("forjador.command", "mmocore admin exp give %player% %profession% %xp% false")
                .replace("%player%", player.getName())
                .replace("%profession%", profession)
                .replace("%xp%", xp)
                .replace("%recipe%", recipeId == null ? "receta" : recipeId);

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

        if (plugin.getConfig().getBoolean("forjador.notify-xp", true)) {
            String message = plugin.getConfig().getString("forjador.message", "&8[&6Forjador&8] &7+&e%xp% XP&7.")
                    .replace("%xp%", xp)
                    .replace("%recipe%", recipeId == null ? "receta" : recipeId);
            player.sendMessage(ColorUtil.color(message));
        }
    }
}
