package com.mdvcraft.mdvrecetas.listener;

import com.mdvcraft.mdvrecetas.MDVRecetasPlugin;
import com.mdvcraft.mdvrecetas.model.MdvRecipe;
import com.mdvcraft.mdvrecetas.service.ForjadorXpService;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class CookingXpListener implements Listener {
    private final MDVRecetasPlugin plugin;
    private final ForjadorXpService xpService;

    /**
     * Receta custom detectada cuando el horno empieza a cocinar.
     *
     * Esto es importante para MMOItems: al final de la coccion algunos builds
     * pueden entregar en FurnaceSmeltEvent#getSource() un ItemStack simplificado
     * por material base, sin el NBT real del MMOItem. Guardar la receta al inicio
     * evita que el horno entre en bucle por no reconocer el item al final.
     */
    private final Map<String, NamespacedKey> activeCookingRecipes = new HashMap<>();

    /**
     * XP acumulada por bloque de horno.
     *
     * Antes se guardaba en PersistentDataContainer del TileState durante
     * FurnaceSmeltEvent. Eso puede forzar una actualizacion del BlockState en
     * mitad del procesamiento del horno y provocar que la barra se reinicie o
     * que no aparezca el resultado. Por eso ahora es memoria temporal.
     */
    private final Map<String, PendingCookingXp> pendingXp = new HashMap<>();

    public CookingXpListener(MDVRecetasPlugin plugin, ForjadorXpService xpService) {
        this.plugin = plugin;
        this.xpService = xpService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnaceStartSmelt(FurnaceStartSmeltEvent event) {
        ItemStack source = event.getSource();
        if (source == null || source.getType().isAir()) {
            source = liveCookingSource(event.getBlock());
        }

        Optional<MdvRecipe> match = plugin.getRecipeManager().findCookingRecipe(event.getBlock(), source);
        String blockKey = blockKey(event.getBlock());

        if (match.isEmpty()) {
            activeCookingRecipes.remove(blockKey);
            return;
        }

        MdvRecipe recipe = match.get();
        activeCookingRecipes.put(blockKey, recipe.getKey());

        if (recipe.getCookingTime() > 0) {
            event.setTotalCookTime(recipe.getCookingTime());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        String blockKey = blockKey(event.getBlock());
        MdvRecipe recipe = null;

        NamespacedKey activeKey = activeCookingRecipes.remove(blockKey);
        if (activeKey != null) {
            recipe = plugin.getRecipeManager().getByKey(activeKey).orElse(null);
        }

        ItemStack source = event.getSource();
        if (recipe == null) {
            ItemStack liveSource = liveCookingSource(event.getBlock());
            if (liveSource != null && !liveSource.getType().isAir()) {
                source = liveSource;
            }
            recipe = plugin.getRecipeManager().findCookingRecipe(event.getBlock(), source).orElse(null);
        }

        if (recipe == null) {
            // Las recetas de horno custom se registran por material base para que
            // Minecraft permita iniciar la coccion. Si llega al final un item con
            // ese material base pero que NO era el custom correcto, cancelamos el
            // resultado para evitar exploits tipo GOLD_NUGGET vanilla -> item custom.
            if (plugin.getRecipeManager().hasCookingRecipeWithInputMaterial(event.getBlock(), source)) {
                event.setCancelled(true);
            }
            return;
        }

        ItemStack result = plugin.getItemResolver().buildItem(recipe.getResult());
        if (result == null || result.getType().isAir()) {
            event.setCancelled(true);
            return;
        }

        event.setResult(result.clone());

        double xp = recipe.getForjador().getExp() * Math.max(1, result.getAmount());
        if (xp > 0) {
            addPendingXp(blockKey, recipe, xp);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        String blockKey = blockKey(block);
        PendingCookingXp pending = pendingXp.remove(blockKey);
        if (pending == null || pending.xp() <= 0) {
            return;
        }

        xpService.award(player, block.getLocation(), pending.xp(), pending.recipeId());
    }

    private ItemStack liveCookingSource(Block block) {
        if (block == null) {
            return null;
        }
        if (!(block.getState() instanceof Furnace furnace)) {
            return null;
        }
        ItemStack input = furnace.getInventory().getSmelting();
        if (input == null || input.getType().isAir()) {
            return null;
        }
        return input.clone();
    }

    private void addPendingXp(String blockKey, MdvRecipe recipe, double xp) {
        PendingCookingXp current = pendingXp.get(blockKey);
        if (current == null) {
            pendingXp.put(blockKey, new PendingCookingXp(xp, recipe.getId()));
            return;
        }
        pendingXp.put(blockKey, new PendingCookingXp(current.xp() + xp, recipe.getId()));
    }

    private String blockKey(Block block) {
        if (block == null || block.getWorld() == null) {
            return "unknown";
        }
        Location location = block.getLocation();
        UUID worldId = block.getWorld().getUID();
        return worldId + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private record PendingCookingXp(double xp, String recipeId) {
    }
}
