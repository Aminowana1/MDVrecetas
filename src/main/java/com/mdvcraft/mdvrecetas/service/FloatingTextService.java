package com.mdvcraft.mdvrecetas.service;

import com.mdvcraft.mdvrecetas.MDVRecetasPlugin;
import com.mdvcraft.mdvrecetas.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Administra los textos flotantes de experiencia de Forjador.
 *
 * Desde 0.6.13 los avisos cercanos se reutilizan y acumulan en una sola
 * entidad. Esto evita crear decenas o cientos de TextDisplay cuando un jugador
 * fabrica muchos objetos de poca experiencia en pocos ticks.
 */
public final class FloatingTextService {
    private final MDVRecetasPlugin plugin;
    private final NamespacedKey hologramKey;
    private final Map<UUID, ActiveHologram> activeHolograms = new HashMap<>();
    private final DecimalFormat decimalFormat;

    public FloatingTextService(MDVRecetasPlugin plugin) {
        this.plugin = plugin;
        this.hologramKey = new NamespacedKey(plugin, "forjador_xp_hologram");

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        this.decimalFormat = new DecimalFormat("0.##", symbols);
    }

    public void spawnXpText(Location baseLocation, double awardedXp, String recipeId) {
        if (baseLocation == null
                || awardedXp <= 0D
                || !plugin.getConfig().getBoolean("forjador.hologram.enabled", true)) {
            return;
        }

        World world = baseLocation.getWorld();
        if (world == null) {
            return;
        }

        pruneInvalidHolograms();

        double yOffset = plugin.getConfig().getDouble("forjador.hologram.y-offset", 1.35D);
        Location displayLocation = baseLocation.clone().add(0.5D, yOffset, 0.5D);
        int durationTicks = Math.max(5, plugin.getConfig().getInt("forjador.hologram.duration-ticks", 35));
        boolean singlePerRadius = plugin.getConfig().getBoolean("forjador.hologram.single-per-radius", true);
        boolean accumulateXp = plugin.getConfig().getBoolean("forjador.hologram.accumulate-xp", true);
        double mergeRadius = Math.max(0.5D, plugin.getConfig().getDouble("forjador.hologram.merge-radius", 8.0D));

        ActiveHologram target = singlePerRadius
                ? findNearestActive(world, displayLocation, mergeRadius)
                : null;

        if (target != null) {
            double totalXp = accumulateXp ? target.accumulatedXp + awardedXp : awardedXp;

            // Por seguridad elimina cualquier otro holograma de MDVRecetas que
            // haya quedado dentro del mismo radio y suma su valor al principal.
            totalXp += removeOtherActiveNearby(target, world, displayLocation, mergeRadius, accumulateXp);

            target.accumulatedXp = totalXp;
            target.recipeId = recipeId;
            target.display.teleport(displayLocation);
            updateDisplay(target.display, totalXp, recipeId);
            rescheduleRemoval(target, durationTicks);
            return;
        }

        if (singlePerRadius) {
            // Limpia TextDisplay etiquetados que pudieran haber quedado de un
            // reload anterior sin estado en memoria antes de crear el nuevo.
            removeOrphanTaggedNearby(world, displayLocation, mergeRadius, null);
        }

        TextDisplay display = world.spawn(displayLocation, TextDisplay.class, entity -> {
            entity.getPersistentDataContainer().set(hologramKey, PersistentDataType.BYTE, (byte) 1);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setSeeThrough(true);
            entity.setShadowed(false);
            entity.setPersistent(false);
            entity.setViewRange((float) plugin.getConfig().getDouble("forjador.hologram.view-range", 24.0D));
            updateDisplay(entity, awardedXp, recipeId);
        });

        ActiveHologram active = new ActiveHologram(display, awardedXp, recipeId);
        activeHolograms.put(display.getUniqueId(), active);
        rescheduleRemoval(active, durationTicks);
    }

    /**
     * Elimina hologramas etiquetados que hayan sobrevivido a un /reload o a una
     * desactivación inesperada del plugin. Se ejecuta una sola vez al iniciar.
     */
    public void cleanupStaleDisplays() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof TextDisplay display && isOwnedHologram(display)) {
                    display.remove();
                }
            }
        }
        activeHolograms.clear();
    }

    /** Elimina tareas y entidades activas al apagar o recargar el plugin. */
    public void shutdown() {
        for (ActiveHologram active : new ArrayList<>(activeHolograms.values())) {
            removeActive(active);
        }
        activeHolograms.clear();
    }

    private void updateDisplay(TextDisplay display, double xp, String recipeId) {
        String formattedXp = decimalFormat.format(Math.max(0D, xp));
        String rawText = plugin.getConfig().getString("forjador.hologram.text", "&e+%xp%EXP!")
                .replace("%xp%", formattedXp)
                .replace("%recipe%", recipeId == null ? "receta" : recipeId);
        display.setText(ColorUtil.color(rawText));
    }

    private ActiveHologram findNearestActive(World world, Location location, double radius) {
        double radiusSquared = radius * radius;
        ActiveHologram nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (ActiveHologram active : activeHolograms.values()) {
            TextDisplay display = active.display;
            if (!display.isValid() || !display.getWorld().equals(world)) {
                continue;
            }

            double distanceSquared = display.getLocation().distanceSquared(location);
            if (distanceSquared <= radiusSquared && distanceSquared < nearestDistance) {
                nearest = active;
                nearestDistance = distanceSquared;
            }
        }
        return nearest;
    }

    private double removeOtherActiveNearby(ActiveHologram kept, World world, Location location,
                                           double radius, boolean accumulateXp) {
        double radiusSquared = radius * radius;
        double mergedXp = 0D;
        List<ActiveHologram> toRemove = new ArrayList<>();

        for (ActiveHologram active : activeHolograms.values()) {
            if (active == kept || !active.display.isValid() || !active.display.getWorld().equals(world)) {
                continue;
            }
            if (active.display.getLocation().distanceSquared(location) <= radiusSquared) {
                if (accumulateXp) {
                    mergedXp += active.accumulatedXp;
                }
                toRemove.add(active);
            }
        }

        for (ActiveHologram active : toRemove) {
            removeActive(active);
        }
        return mergedXp;
    }

    private void removeOrphanTaggedNearby(World world, Location location, double radius, UUID keptId) {
        for (Entity entity : world.getNearbyEntities(location, radius, radius, radius)) {
            if (!(entity instanceof TextDisplay display)) {
                continue;
            }
            if (keptId != null && keptId.equals(display.getUniqueId())) {
                continue;
            }
            if (!isOwnedHologram(display)) {
                continue;
            }
            if (display.getLocation().distanceSquared(location) > radius * radius) {
                continue;
            }

            ActiveHologram active = activeHolograms.remove(display.getUniqueId());
            if (active != null && active.removalTask != null) {
                active.removalTask.cancel();
            }
            display.remove();
        }
    }

    private boolean isOwnedHologram(TextDisplay display) {
        Byte marker = display.getPersistentDataContainer().get(hologramKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private void rescheduleRemoval(ActiveHologram active, int durationTicks) {
        if (active.removalTask != null) {
            active.removalTask.cancel();
        }

        UUID displayId = active.display.getUniqueId();
        active.removalTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            ActiveHologram current = activeHolograms.remove(displayId);
            if (current != null && current.display.isValid()) {
                current.display.remove();
            }
        }, durationTicks);
    }

    private void pruneInvalidHolograms() {
        List<ActiveHologram> invalid = new ArrayList<>();
        for (ActiveHologram active : activeHolograms.values()) {
            if (!active.display.isValid()) {
                invalid.add(active);
            }
        }
        for (ActiveHologram active : invalid) {
            removeActive(active);
        }
    }

    private void removeActive(ActiveHologram active) {
        if (active == null) {
            return;
        }
        activeHolograms.remove(active.display.getUniqueId());
        if (active.removalTask != null) {
            active.removalTask.cancel();
            active.removalTask = null;
        }
        if (active.display.isValid()) {
            active.display.remove();
        }
    }

    private static final class ActiveHologram {
        private final TextDisplay display;
        private double accumulatedXp;
        private String recipeId;
        private BukkitTask removalTask;

        private ActiveHologram(TextDisplay display, double accumulatedXp, String recipeId) {
            this.display = display;
            this.accumulatedXp = accumulatedXp;
            this.recipeId = recipeId;
        }
    }
}
