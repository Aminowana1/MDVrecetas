package com.mdvcraft.mdvrecetas.recipe;

import com.mdvcraft.mdvrecetas.MDVRecetasPlugin;
import org.bukkit.NamespacedKey;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Incremental recipe synchronization.
 *
 * Bukkit/Purpur can perform expensive resource/advancement work on addRecipe.
 * Therefore unchanged recipes must never be removed and re-added during a
 * normal MDVRecetas reload.
 */
final class RecipeReloadService {
    private final MDVRecetasPlugin plugin;
    private final MdvRecipeManager manager;
    private final RecipeDiskScanner scanner;
    private final Map<NamespacedKey, RecipeDiskEntry> loadedEntries = new LinkedHashMap<>();

    RecipeReloadService(MDVRecetasPlugin plugin, MdvRecipeManager manager, RecipeParser parser) {
        this.plugin = plugin;
        this.manager = manager;
        this.scanner = new RecipeDiskScanner(plugin, parser);
    }

    int reloadIncrementally() {
        Map<NamespacedKey, RecipeDiskEntry> desired = scanner.scanAll();

        int added = 0;
        int updated = 0;
        int removed = 0;
        int unchanged = 0;

        // Remove recipes which no longer exist (or became disabled/invalid).
        for (NamespacedKey currentKey : loadedEntries.keySet().toArray(NamespacedKey[]::new)) {
            if (!desired.containsKey(currentKey)) {
                if (manager.removeRuntimeRecipeByKey(currentKey)) {
                    removed++;
                }
                loadedEntries.remove(currentKey);
            }
        }

        for (Map.Entry<NamespacedKey, RecipeDiskEntry> desiredEntry : desired.entrySet()) {
            NamespacedKey key = desiredEntry.getKey();
            RecipeDiskEntry next = desiredEntry.getValue();
            RecipeDiskEntry current = loadedEntries.get(key);

            if (current != null
                    && current.fingerprint().equals(next.fingerprint())
                    && current.recipe().getId().equals(next.recipe().getId())) {
                // Moving a recipe to another YAML does not require touching Bukkit.
                manager.updateRuntimeSource(next.recipe().getId(), next.sourceFile());
                loadedEntries.put(key, next);
                unchanged++;
                continue;
            }

            try {
                if (current == null) {
                    manager.addRuntimeRecipe(next.recipe(), next.sourceFile());
                    added++;
                } else {
                    manager.replaceRuntimeRecipe(next.recipe(), next.sourceFile());
                    updated++;
                }
                loadedEntries.put(key, next);
            } catch (Exception exception) {
                plugin.getLogger().warning("Could not " + (current == null ? "register" : "update")
                        + " recipe '" + next.recipe().getId() + "': " + exception.getMessage());
            }
        }

        // Keep the snapshot aligned with recipes that actually remain loaded.
        loadedEntries.keySet().removeIf(key -> manager.getByKey(key).isEmpty());

        int total = manager.getRecipes().size();
        plugin.getLogger().info("Recipe sync complete: " + total + " loaded ("
                + added + " added, " + updated + " updated, " + removed + " removed, "
                + unchanged + " unchanged).");
        return total;
    }

    /**
     * Synchronizes only the recipe just saved by the editor.
     * No directory-wide reload and no re-registration of unrelated recipes.
     */
    int synchronizeSavedRecipe(String previousId, String currentId, File sourceFile) {
        if (previousId != null && !previousId.isBlank() && !previousId.equalsIgnoreCase(currentId)) {
            removeById(previousId);
        }

        RecipeDiskEntry next = scanner.scanOne(sourceFile, currentId);
        if (next == null) {
            // The editor has already verified the YAML node, so reaching this
            // path means the node was disabled or could not be parsed.
            removeById(currentId);
            return manager.getRecipes().size();
        }

        NamespacedKey key = next.recipe().getKey();
        RecipeDiskEntry current = loadedEntries.get(key);
        try {
            if (current == null && manager.getByKey(key).isEmpty()) {
                manager.addRuntimeRecipe(next.recipe(), next.sourceFile());
            } else if (current != null
                    && current.fingerprint().equals(next.fingerprint())
                    && current.recipe().getId().equals(next.recipe().getId())) {
                manager.updateRuntimeSource(next.recipe().getId(), next.sourceFile());
            } else {
                manager.replaceRuntimeRecipe(next.recipe(), next.sourceFile());
            }
            loadedEntries.put(key, next);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Could not synchronize saved recipe '" + currentId + "': " + exception.getMessage());
            throw exception;
        }
        return manager.getRecipes().size();
    }

    int removeById(String id) {
        if (id == null || id.isBlank()) {
            return manager.getRecipes().size();
        }
        NamespacedKey found = null;
        for (Map.Entry<NamespacedKey, RecipeDiskEntry> entry : loadedEntries.entrySet()) {
            if (entry.getValue().recipe().getId().equalsIgnoreCase(id)) {
                found = entry.getKey();
                break;
            }
        }
        if (found == null) {
            found = manager.findRuntimeKeyById(id);
        }
        if (found != null) {
            manager.removeRuntimeRecipeByKey(found);
            loadedEntries.remove(found);
        }
        return manager.getRecipes().size();
    }

    void clearSnapshots() {
        loadedEntries.clear();
    }
}
