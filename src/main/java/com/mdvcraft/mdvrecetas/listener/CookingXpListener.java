package com.mdvcraft.mdvrecetas.listener;

import com.mdvcraft.mdvrecetas.MDVRecetasPlugin;
import com.mdvcraft.mdvrecetas.model.MdvRecipe;
import com.mdvcraft.mdvrecetas.service.ForjadorXpService;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        plugin.getRecipeManager().findCookingRecipe(event.getBlock(), event.getSource(), event.getResult()).ifPresent(recipe -> {
            double xp = recipe.getForjador().getExp() * Math.max(1, event.getResult().getAmount());
            if (xp <= 0) {
                return;
            }
            addPendingXp(event.getBlock(), recipe, xp);
        });
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
