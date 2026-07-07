package com.mdvcraft.mdvrecetas.listener;

import com.mdvcraft.mdvrecetas.MDVRecetasPlugin;
import com.mdvcraft.mdvrecetas.model.MdvRecipe;
import com.mdvcraft.mdvrecetas.service.ForjadorXpService;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Furnace;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class CookingXpListener implements Listener {
    private final MDVRecetasPlugin plugin;
    private final ForjadorXpService xpService;
    private final NamespacedKey pendingXpKey;
    private final NamespacedKey lastRecipeKey;

    public CookingXpListener(MDVRecetasPlugin plugin, ForjadorXpService xpService) {
        this.plugin = plugin;
        this.xpService = xpService;
        this.pendingXpKey = new NamespacedKey(plugin, "pending_forjador_xp");
        this.lastRecipeKey = new NamespacedKey(plugin, "pending_forjador_recipe");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        /*
         * En algunos builds Paper/Purpur, FurnaceSmeltEvent#getSource() puede venir como
         * el item de la RecipeChoice/material base y no como el ItemStack real con NBT.
         * Eso rompe MMOItems: el horno empieza a cocinar por MaterialChoice, pero al final
         * MDVRecetas no reconoce el MMOItem y cancela, causando el bucle visual de coccion.
         *
         * Por eso validamos primero el item real que sigue dentro del inventario del horno.
         */
        ItemStack source = liveCookingSource(event.getBlock());
        if (source == null || source.getType().isAir()) {
            source = event.getSource();
        }

        var match = plugin.getRecipeManager().findCookingRecipe(event.getBlock(), source);
        if (match.isEmpty()) {
            // La receta se registra por material base para que el horno pueda avanzar.
            // Si el material base coincide pero el item real no es el custom esperado,
            // se cancela para evitar convertir items vanilla en resultados custom.
            if (plugin.getRecipeManager().hasCookingRecipeWithInputMaterial(event.getBlock(), source)) {
                event.setCancelled(true);
            }
            return;
        }

        MdvRecipe recipe = match.get();
        ItemStack result = plugin.getItemResolver().buildItem(recipe.getResult());
        if (result == null || result.getType().isAir()) {
            event.setCancelled(true);
            return;
        }

        event.setResult(result.clone());
        double xp = recipe.getForjador().getExp() * Math.max(1, result.getAmount());
        if (xp <= 0) {
            return;
        }
        addPendingXp(event.getBlock(), recipe, xp);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (!(block.getState() instanceof TileState tileState)) {
            return;
        }
        PersistentDataContainer container = tileState.getPersistentDataContainer();
        Double pending = container.get(pendingXpKey, PersistentDataType.DOUBLE);
        String recipeId = container.get(lastRecipeKey, PersistentDataType.STRING);
        if (pending == null || pending <= 0) {
            return;
        }

        container.remove(pendingXpKey);
        container.remove(lastRecipeKey);
        tileState.update(true, false);
        xpService.award(player, block.getLocation(), pending, recipeId == null ? "cooking" : recipeId);
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

    private void addPendingXp(Block block, MdvRecipe recipe, double xp) {
        if (!(block.getState() instanceof TileState tileState)) {
            return;
        }
        PersistentDataContainer container = tileState.getPersistentDataContainer();
        double current = container.getOrDefault(pendingXpKey, PersistentDataType.DOUBLE, 0.0D);
        container.set(pendingXpKey, PersistentDataType.DOUBLE, current + xp);
        container.set(lastRecipeKey, PersistentDataType.STRING, recipe.getId());
        tileState.update(true, false);
    }
}
