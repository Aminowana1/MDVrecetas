package com.mdvcraft.mdvrecetas.listener;

import com.mdvcraft.mdvrecetas.MDVRecetasPlugin;
import com.mdvcraft.mdvrecetas.service.ForjadorXpService;
import com.mdvcraft.mdvrecetas.service.RecipeSignatureService;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

public final class RecipeCraftListener implements Listener {
    private final MDVRecetasPlugin plugin;
    private final ForjadorXpService xpService;
    private final RecipeSignatureService signatureService;

    public RecipeCraftListener(MDVRecetasPlugin plugin, ForjadorXpService xpService, RecipeSignatureService signatureService) {
        this.plugin = plugin;
        this.xpService = xpService;
        this.signatureService = signatureService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Recipe recipe = event.getRecipe();
        if (!(recipe instanceof Keyed keyed)) {
            return;
        }

        plugin.getRecipeManager().getByKey(keyed.getKey()).ifPresent(mdvRecipe -> {
            if (mdvRecipe.getForjador().isSignature()) {
                ItemStack current = event.getCurrentItem();
                if (current == null || current.getType().isAir()) {
                    current = recipe.getResult();
                }
                if (current != null && !current.getType().isAir()) {
                    ItemStack signed = signatureService.applySignature(current, player, mdvRecipe.getId());
                    event.setCurrentItem(signed);
                    if (event.getInventory() instanceof CraftingInventory craftingInventory) {
                        craftingInventory.setResult(signed.clone());
                    }
                }
            }

            int crafts = estimateCrafts(event);
            double xp = mdvRecipe.getForjador().getExp() * Math.max(1, crafts);
            Location visualLocation = event.getInventory().getLocation();
            if (visualLocation == null) {
                visualLocation = player.getLocation();
            }
            xpService.award(player, visualLocation, xp, mdvRecipe.getId());
        });
    }

    private int estimateCrafts(CraftItemEvent event) {
        ItemStack result = event.getRecipe().getResult();
        int resultAmount = result == null ? 1 : Math.max(1, result.getAmount());

        if (!event.isShiftClick()) {
            ItemStack current = event.getCurrentItem();
            if (current == null || current.getType().isAir()) {
                return 1;
            }
            return Math.max(1, current.getAmount() / resultAmount);
        }

        if (!(event.getInventory() instanceof CraftingInventory craftingInventory)) {
            return 1;
        }

        int min = Integer.MAX_VALUE;
        for (ItemStack matrixItem : craftingInventory.getMatrix()) {
            if (matrixItem == null || matrixItem.getType().isAir()) {
                continue;
            }
            min = Math.min(min, matrixItem.getAmount());
        }
        if (min == Integer.MAX_VALUE) {
            return 1;
        }
        return Math.max(1, min);
    }
}
