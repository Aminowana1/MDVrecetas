package com.mdvcraft.mdvrecetas.service;

import com.mdvcraft.mdvrecetas.MDVRecetasPlugin;
import com.mdvcraft.mdvrecetas.util.ColorUtil;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

public final class FloatingTextService {
    private final MDVRecetasPlugin plugin;

    public FloatingTextService(MDVRecetasPlugin plugin) {
        this.plugin = plugin;
    }

    public void spawnXpText(Location baseLocation, String xp, String recipeId) {
        if (baseLocation == null || !plugin.getConfig().getBoolean("forjador.hologram.enabled", true)) {
            return;
        }
        World world = baseLocation.getWorld();
        if (world == null) {
            return;
        }

        double yOffset = plugin.getConfig().getDouble("forjador.hologram.y-offset", 1.35D);
        Location location = baseLocation.clone().add(0.5D, yOffset, 0.5D);
        String rawText = plugin.getConfig().getString("forjador.hologram.text", "&e+%xp%EXP!")
                .replace("%xp%", xp)
                .replace("%recipe%", recipeId == null ? "receta" : recipeId);
        int durationTicks = Math.max(5, plugin.getConfig().getInt("forjador.hologram.duration-ticks", 35));

        TextDisplay display = world.spawn(location, TextDisplay.class, entity -> {
            entity.setText(ColorUtil.color(rawText));
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setSeeThrough(true);
            entity.setShadowed(false);
            entity.setPersistent(false);
            entity.setViewRange((float) plugin.getConfig().getDouble("forjador.hologram.view-range", 24.0D));
        });

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (display.isValid()) {
                display.remove();
            }
        }, durationTicks);
    }
}
