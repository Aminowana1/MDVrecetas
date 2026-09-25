package com.mdvcraft.mdvrecetas.listener;

import com.mdvcraft.mdvrecetas.MDVRecetasPlugin;
import com.mdvcraft.mdvrecetas.api.event.MDVRecipeCraftEvent;
import com.mdvcraft.mdvrecetas.model.MdvRecipe;
import com.mdvcraft.mdvrecetas.service.ForjadorModifierService;
import com.mdvcraft.mdvrecetas.service.ForjadorXpService;
import com.mdvcraft.mdvrecetas.service.RecipeSignatureService;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;

import java.util.HashMap;
import java.util.Map;

public final class RecipeCraftListener implements Listener {
    private final MDVRecetasPlugin plugin;
    private final ForjadorXpService xpService;
    private final RecipeSignatureService signatureService;
    private final ForjadorModifierService modifierService;

    public RecipeCraftListener(MDVRecetasPlugin plugin, ForjadorXpService xpService, RecipeSignatureService signatureService, ForjadorModifierService modifierService) {
        this.plugin = plugin;
        this.xpService = xpService;
        this.signatureService = signatureService;
        this.modifierService = modifierService;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareItemCraftEvent event) {
        if (event.getInventory() == null) {
            return;
        }
        var match = plugin.getRecipeManager().findMatchingCraftingRecipe(event.getInventory().getMatrix());
        if (match.isPresent()) {
            // Vanilla shift-click can consume successive prepared results without
            // a separate CraftItemEvent for each one. Keep these results fresh.
            ItemStack result = plugin.getItemResolver().buildItem(match.get().getResult());
            event.getInventory().setResult(result == null ? null : result.clone());
            return;
        }

        Recipe current = event.getRecipe();
        if (current instanceof Keyed keyed && plugin.getRecipeManager().getByKey(keyed.getKey()).isPresent()) {
            // El patron material pudo coincidir, pero TYPE+ID/EXACT no.
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Recipe recipe = event.getRecipe();
        var matched = plugin.getRecipeManager().findMatchingCraftingRecipe(
                event.getInventory() instanceof CraftingInventory crafting ? crafting.getMatrix() : null
        );
        if (matched.isEmpty()) {
            return;
        }

        MdvRecipe mdvRecipe = matched.get();
        {
            // Cuando hay firma o modificadores, cada item creado debe procesarse por separado.
            // Si dejamos que Bukkit resuelva shift-click/click derecho, puede crear varios resultados
            // pero solo el primer ItemStack pasa por nuestra firma/roll. Por eso interceptamos esos
            // clicks y hacemos el craft manualmente, generando un roll independiente por cada item.
            if (needsPerItemProcessing(mdvRecipe) && (event.isShiftClick() || event.isRightClick())) {
                craftManually(event, player, recipe, mdvRecipe);
                return;
            }

            ItemStack current = plugin.getItemResolver().buildItem(mdvRecipe.getResult());
            if (current == null || current.getType().isAir()) {
                current = event.getCurrentItem();
            }
            if (current != null && !current.getType().isAir()) {
                ItemStack finalResult = processResult(current, player, mdvRecipe);
                event.setCurrentItem(finalResult);
                if (event.getInventory() instanceof CraftingInventory craftingInventory) {
                    craftingInventory.setResult(finalResult.clone());
                }
            }

            int crafts = estimateCrafts(event);
            awardCraftXp(player, event.getInventory().getLocation(), mdvRecipe, crafts);
            ItemStack eventResult = event.getCurrentItem();
            if (eventResult == null || eventResult.getType().isAir()) {
                eventResult = plugin.getItemResolver().buildItem(mdvRecipe.getResult());
            }
            int unitAmount = mdvRecipe.getResult().getAmount();
            int produced = unitAmount * Math.max(1, crafts);
            plugin.getServer().getPluginManager().callEvent(new MDVRecipeCraftEvent(
                    player, mdvRecipe.getId(), mdvRecipe.getCategory(), mdvRecipe.getStation(),
                    eventResult, crafts, produced, false));
        }
    }

    private boolean needsPerItemProcessing(MdvRecipe recipe) {
        return recipe != null
                && recipe.getForjador() != null
                && (recipe.getForjador().isSignature() || recipe.getForjador().isModifiers());
    }

    private void craftManually(CraftItemEvent event, Player player, Recipe bukkitRecipe, MdvRecipe mdvRecipe) {
        if (!(event.getInventory() instanceof CraftingInventory craftingInventory)) {
            return;
        }

        int crafts = Math.max(1, estimateCrafts(event));
        ItemStack[] matrix = craftingInventory.getMatrix();
        int possible = maxCraftsFromMatrix(matrix);
        crafts = Math.max(1, Math.min(crafts, possible));

        ItemStack baseResult = plugin.getItemResolver().buildItem(mdvRecipe.getResult());
        if (baseResult == null || baseResult.getType().isAir()) {
            return;
        }

        event.setCancelled(true);

        consumeMatrix(matrix, crafts);
        craftingInventory.setMatrix(matrix);
        craftingInventory.setResult(null);

        for (int i = 0; i < crafts; i++) {
            ItemStack singleResult = baseResult.clone();
            singleResult.setAmount(Math.max(1, baseResult.getAmount()));
            singleResult = processResult(singleResult, player, mdvRecipe);
            giveOrDrop(player, singleResult);
        }

        awardCraftXp(player, craftingInventory.getLocation(), mdvRecipe, crafts);
        int produced = Math.max(1, baseResult.getAmount()) * Math.max(1, crafts);
        plugin.getServer().getPluginManager().callEvent(new MDVRecipeCraftEvent(
                player, mdvRecipe.getId(), mdvRecipe.getCategory(), mdvRecipe.getStation(),
                baseResult, crafts, produced, false));

        plugin.getServer().getScheduler().runTask(plugin, player::updateInventory);
    }

    private ItemStack processResult(ItemStack input, Player player, MdvRecipe mdvRecipe) {
        ItemStack finalResult = modifierService.applyModifierIfNeeded(input, player, mdvRecipe);
        if (mdvRecipe.getForjador().isSignature()) {
            finalResult = signatureService.applySignature(finalResult, player, mdvRecipe.getId());
        }
        return finalResult;
    }

    private void awardCraftXp(Player player, Location location, MdvRecipe recipe, int crafts) {
        double xp = recipe.getForjador().getExp() * Math.max(1, crafts);
        Location visualLocation = location;
        if (visualLocation == null) {
            visualLocation = player.getLocation();
        }
        xpService.award(player, visualLocation, xp, recipe.getId());
    }

    private void giveOrDrop(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        Map<Integer, ItemStack> leftovers = inventory.addItem(item);
        for (ItemStack leftover : leftovers.values()) {
            if (leftover != null && !leftover.getType().isAir()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    private int maxCraftsFromMatrix(ItemStack[] matrix) {
        int min = Integer.MAX_VALUE;
        if (matrix == null) {
            return 1;
        }
        for (ItemStack item : matrix) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            min = Math.min(min, Math.max(0, item.getAmount()));
        }
        return min == Integer.MAX_VALUE ? 1 : Math.max(1, min);
    }

    private void consumeMatrix(ItemStack[] matrix, int crafts) {
        if (matrix == null) {
            return;
        }
        for (int i = 0; i < matrix.length; i++) {
            ItemStack item = matrix[i];
            if (item == null || item.getType().isAir()) {
                continue;
            }
            int newAmount = item.getAmount() - crafts;
            if (newAmount <= 0) {
                matrix[i] = new ItemStack(Material.AIR);
            } else {
                item.setAmount(newAmount);
                matrix[i] = item;
            }
        }
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
