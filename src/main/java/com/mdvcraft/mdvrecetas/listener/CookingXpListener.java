package com.mdvcraft.mdvrecetas.listener;

import com.mdvcraft.mdvrecetas.MDVRecetasPlugin;
import com.mdvcraft.mdvrecetas.api.event.MDVRecipeCraftEvent;
import com.mdvcraft.mdvrecetas.model.MdvRecipe;
import com.mdvcraft.mdvrecetas.service.ForjadorXpService;
import com.mdvcraft.mdvrecetas.service.RecipeSignatureService;
import com.mdvcraft.mdvrecetas.service.ForjadorModifierService;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Iterator;

public final class CookingXpListener implements Listener {
    private final MDVRecetasPlugin plugin;
    private final ForjadorXpService xpService;
    private final RecipeSignatureService signatureService;
    private final ForjadorModifierService modifierService;

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

    public CookingXpListener(MDVRecetasPlugin plugin, ForjadorXpService xpService, RecipeSignatureService signatureService, ForjadorModifierService modifierService) {
        this.plugin = plugin;
        this.xpService = xpService;
        this.signatureService = signatureService;
        this.modifierService = modifierService;
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
        addPendingXp(blockKey, recipe, xp, Math.max(1, result.getAmount()));
    }



    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnaceResultClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getRawSlot() != 2) {
            return;
        }

        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Furnace furnace)) {
            return;
        }

        String blockKey = blockKey(furnace.getBlock());
        PendingCookingXp pending = pendingXp.get(blockKey);
        if (pending == null) {
            return;
        }

        MdvRecipe recipe = plugin.getRecipeManager().getByKey(pending.recipeKey()).orElse(null);
        if (recipe == null || recipe.getForjador() == null) {
            return;
        }

        ItemStack result = top.getItem(2);
        if (result == null || result.getType().isAir()) {
            return;
        }

        ItemStack finalResult = modifierService.applyModifierIfNeeded(result, player, recipe);
        if (recipe.getForjador().isSignature()) {
            finalResult = signatureService.applySignature(finalResult, player, recipe.getId());
        }
        top.setItem(2, finalResult);
        event.setCurrentItem(finalResult.clone());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        String blockKey = blockKey(block);
        PendingCookingXp pending = pendingXp.get(blockKey);
        if (pending == null || pending.producedItems() <= 0) {
            return;
        }

        int extracted = Math.max(1, event.getItemAmount());
        int countedItems = Math.min(extracted, pending.producedItems());
        double awardedXp = pending.xp() * (countedItems / (double) pending.producedItems());
        int remainingItems = pending.producedItems() - countedItems;
        double remainingXp = Math.max(0D, pending.xp() - awardedXp);
        if (remainingItems <= 0) pendingXp.remove(blockKey);
        else pendingXp.put(blockKey, new PendingCookingXp(remainingXp, pending.recipeId(), pending.recipeKey(), remainingItems, pending.resultAmount()));

        if (awardedXp > 0) xpService.award(player, block.getLocation(), awardedXp, pending.recipeId());

        MdvRecipe recipe = plugin.getRecipeManager().getByKey(pending.recipeKey()).orElse(null);
        if (recipe != null) {
            ItemStack result = plugin.getItemResolver().buildItem(recipe.getResult());
            int operations = Math.max(1, (int) Math.ceil(countedItems / (double) Math.max(1, pending.resultAmount())));
            plugin.getServer().getPluginManager().callEvent(new MDVRecipeCraftEvent(
                    player, recipe.getId(), recipe.getCategory(), recipe.getStation(), result, operations, countedItems, true));
        }
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceBreak(BlockBreakEvent event) {
        clearBlock(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            clearBlock(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            clearBlock(block);
        }
    }

    public void clearBlock(Block block) {
        if (block == null) {
            return;
        }
        String key = blockKey(block);
        activeCookingRecipes.remove(key);
        pendingXp.remove(key);
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

    private void addPendingXp(String blockKey, MdvRecipe recipe, double xp, int producedItems) {
        PendingCookingXp current = pendingXp.get(blockKey);
        if (current == null || !current.recipeKey().equals(recipe.getKey())) {
            pendingXp.put(blockKey, new PendingCookingXp(xp, recipe.getId(), recipe.getKey(), producedItems, Math.max(1, recipe.getResult().getAmount())));
            return;
        }
        pendingXp.put(blockKey, new PendingCookingXp(current.xp() + xp, recipe.getId(), recipe.getKey(),
                current.producedItems() + producedItems, Math.max(1, recipe.getResult().getAmount())));
    }

    private String blockKey(Block block) {
        if (block == null || block.getWorld() == null) {
            return "unknown";
        }
        Location location = block.getLocation();
        UUID worldId = block.getWorld().getUID();
        return worldId + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private record PendingCookingXp(double xp, String recipeId, NamespacedKey recipeKey, int producedItems, int resultAmount) {
    }
}
