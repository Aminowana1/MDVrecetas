package com.mdvcraft.mdvrecetas.service;

import com.mdvcraft.mdvrecetas.MDVRecetasPlugin;
import com.mdvcraft.mdvrecetas.util.ColorUtil;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class RecipeSignatureService {
    private final MDVRecetasPlugin plugin;
    private final NamespacedKey creatorUuidKey;
    private final NamespacedKey creatorNameKey;
    private final NamespacedKey recipeIdKey;

    public RecipeSignatureService(MDVRecetasPlugin plugin) {
        this.plugin = plugin;
        this.creatorUuidKey = new NamespacedKey(plugin, "crafted_by_uuid");
        this.creatorNameKey = new NamespacedKey(plugin, "crafted_by_name");
        this.recipeIdKey = new NamespacedKey(plugin, "crafted_recipe_id");
    }

    public ItemStack applySignature(ItemStack original, Player player, String recipeId) {
        if (original == null || original.getType().isAir() || player == null) {
            return original;
        }
        if (!plugin.getConfig().getBoolean("signature.enabled", true)) {
            return original;
        }

        ItemStack item = original.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(creatorUuidKey, PersistentDataType.STRING)) {
            pdc.set(creatorUuidKey, PersistentDataType.STRING, player.getUniqueId().toString());
            pdc.set(creatorNameKey, PersistentDataType.STRING, player.getName());
            pdc.set(recipeIdKey, PersistentDataType.STRING, recipeId == null ? "" : recipeId);

            List<String> lore = meta.hasLore() && meta.getLore() != null
                    ? new ArrayList<>(meta.getLore())
                    : new ArrayList<>();
            appendSignatureIfMissing(lore, signatureLines(player.getName(), recipeId));
            meta.setLore(lore);
        }

        item.setItemMeta(meta);
        return item;
    }


    public boolean hasSignatureData(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null
                && meta.getPersistentDataContainer().has(creatorUuidKey, PersistentDataType.STRING);
    }

    /**
     * MMOItems recreates the ItemStack when a Revision ID changes. Arbitrary
     * Bukkit PDC and lore appended by MDVRecetas are not part of MMOItems'
     * StatHistory, therefore they must be copied to the finished revised item.
     */
    public ItemStack restoreAfterRevision(ItemStack oldItem, ItemStack revisedItem) {
        return restoreAfterMmoItemsMutation(oldItem, revisedItem);
    }

    /**
     * Restores the MDVRecetas signature after MMOItems rebuilds an item for
     * any reason, including Revision ID changes, gem insertion and gem removal.
     */
    public ItemStack restoreAfterMmoItemsMutation(ItemStack oldItem, ItemStack revisedItem) {
        if (oldItem == null || oldItem.getType().isAir()
                || revisedItem == null || revisedItem.getType().isAir()) {
            return revisedItem;
        }

        ItemMeta oldMeta = oldItem.getItemMeta();
        if (oldMeta == null) {
            return revisedItem;
        }
        PersistentDataContainer oldPdc = oldMeta.getPersistentDataContainer();
        String creatorUuid = oldPdc.get(creatorUuidKey, PersistentDataType.STRING);
        if (creatorUuid == null || creatorUuid.isBlank()) {
            return revisedItem;
        }

        String creatorName = oldPdc.get(creatorNameKey, PersistentDataType.STRING);
        String recipeId = oldPdc.get(recipeIdKey, PersistentDataType.STRING);
        creatorName = creatorName == null ? "Desconocido" : creatorName;
        recipeId = recipeId == null ? "" : recipeId;

        ItemStack restored = revisedItem.clone();
        ItemMeta newMeta = restored.getItemMeta();
        if (newMeta == null) {
            return revisedItem;
        }

        PersistentDataContainer newPdc = newMeta.getPersistentDataContainer();
        newPdc.set(creatorUuidKey, PersistentDataType.STRING, creatorUuid);
        newPdc.set(creatorNameKey, PersistentDataType.STRING, creatorName);
        newPdc.set(recipeIdKey, PersistentDataType.STRING, recipeId);

        List<String> lore = newMeta.hasLore() && newMeta.getLore() != null
                ? new ArrayList<>(newMeta.getLore())
                : new ArrayList<>();
        appendSignatureIfMissing(lore, signatureLines(creatorName, recipeId));
        newMeta.setLore(lore);

        restored.setItemMeta(newMeta);
        return restored;
    }

    private void appendSignatureIfMissing(List<String> lore, List<String> signature) {
        String marker = lastNonBlank(signature);
        if (marker != null && lore.contains(marker)) {
            return;
        }
        lore.addAll(signature);
    }

    private String lastNonBlank(List<String> lines) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i);
            if (line != null && !ColorUtil.stripColor(line).trim().isEmpty()) {
                return line;
            }
        }
        return null;
    }

    private List<String> signatureLines(String playerName, String recipeId) {
        List<String> rawLines = plugin.getConfig().getStringList("signature.lore-lines");
        if (rawLines == null || rawLines.isEmpty()) {
            rawLines = List.of(plugin.getConfig().getString(
                    "signature.lore-line", "&l&aForjado por: &e%player%"));
        }
        List<String> lines = new ArrayList<>();
        for (String raw : rawLines) {
            String line = raw == null ? "" : raw;
            line = line.replace("%player%", playerName == null ? "Desconocido" : playerName)
                    .replace("%recipe%", recipeId == null ? "" : recipeId);
            lines.add(ColorUtil.color(line));
        }
        return lines;
    }
}
