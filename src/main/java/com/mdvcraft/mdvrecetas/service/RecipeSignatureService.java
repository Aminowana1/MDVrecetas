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

            List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            for (String rawLine : signatureLines(player, recipeId)) {
                lore.add(rawLine);
            }
            meta.setLore(lore);
        }

        item.setItemMeta(meta);
        return item;
    }
    private List<String> signatureLines(Player player, String recipeId) {
        List<String> rawLines = plugin.getConfig().getStringList("signature.lore-lines");
        if (rawLines == null || rawLines.isEmpty()) {
            rawLines = List.of(plugin.getConfig().getString("signature.lore-line", "&l&aForjado por: &e%player%"));
        }
        List<String> lines = new ArrayList<>();
        for (String raw : rawLines) {
            String line = raw == null ? "" : raw;
            line = line.replace("%player%", player.getName()).replace("%recipe%", recipeId == null ? "" : recipeId);
            lines.add(ColorUtil.color(line));
        }
        return lines;
    }

}
