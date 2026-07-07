package com.mdvcraft.mdvrecetas.recipe;

import com.mdvcraft.mdvrecetas.MDVRecetasPlugin;
import com.mdvcraft.mdvrecetas.model.*;
import com.mdvcraft.mdvrecetas.service.ItemResolver;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RecipeParser {
    private final MDVRecetasPlugin plugin;
    private final ItemResolver itemResolver;

    public RecipeParser(MDVRecetasPlugin plugin, ItemResolver itemResolver) {
        this.plugin = plugin;
        this.itemResolver = itemResolver;
    }

    public MdvRecipe parse(String id, ConfigurationSection section) {
        StationType station = enumValue(StationType.class, section.getString("station", "CRAFTING_TABLE"), StationType.CRAFTING_TABLE);
        RecipeType type = enumValue(RecipeType.class, section.getString("type", station.isCookingStation() ? "COOKING" : "SHAPED"), RecipeType.SHAPED);
        String category = section.getString("category", "GENERAL").toUpperCase(Locale.ROOT);
        NamespacedKey key = new NamespacedKey(plugin, sanitizeKey(id));

        ItemSpec result = itemResolver.fromConfig(section.getConfigurationSection("result"));
        ForjadorOptions forjador = parseForjador(section.getConfigurationSection("forjador"));

        boolean replaceVanilla = section.getBoolean("replace-vanilla.enabled", false);
        NamespacedKey vanillaKey = null;
        if (replaceVanilla) {
            vanillaKey = parseNamespacedKey(section.getString("replace-vanilla.vanilla-key"));
            if (vanillaKey == null) {
                throw new IllegalArgumentException("replace-vanilla.enabled is true but vanilla-key is invalid");
            }
        }

        List<String> shape = List.of();
        Map<Character, ItemSpec> shapedIngredients = Map.of();
        Map<String, ItemSpec> shapelessIngredients = Map.of();
        ItemSpec cookingIngredient = null;
        int cookingTime = section.getInt("cooking.time", 200);
        float cookingVanillaExp = (float) section.getDouble("cooking.vanilla-exp", 0.0D);

        if (type == RecipeType.SHAPED) {
            shape = section.getStringList("shape");
            if (shape.isEmpty()) {
                throw new IllegalArgumentException("SHAPED recipe requires shape");
            }
            shapedIngredients = parseShapedIngredients(section.getConfigurationSection("ingredients"));
        } else if (type == RecipeType.SHAPELESS) {
            shapelessIngredients = parseShapelessIngredients(section.getConfigurationSection("ingredients"));
        } else if (type == RecipeType.COOKING) {
            cookingIngredient = itemResolver.fromConfig(section.getConfigurationSection("ingredient"));
        }

        return new MdvRecipe(
                id,
                key,
                station,
                type,
                category,
                shape,
                shapedIngredients,
                shapelessIngredients,
                cookingIngredient,
                result,
                cookingTime,
                cookingVanillaExp,
                forjador,
                replaceVanilla,
                vanillaKey
        );
    }

    private Map<Character, ItemSpec> parseShapedIngredients(ConfigurationSection section) {
        if (section == null) {
            throw new IllegalArgumentException("SHAPED recipe requires ingredients");
        }
        Map<Character, ItemSpec> result = new HashMap<>();
        for (String key : section.getKeys(false)) {
            if (key == null || key.isBlank()) {
                continue;
            }
            result.put(key.charAt(0), itemResolver.fromConfig(section.getConfigurationSection(key)));
        }
        return result;
    }

    private Map<String, ItemSpec> parseShapelessIngredients(ConfigurationSection section) {
        if (section == null) {
            throw new IllegalArgumentException("SHAPELESS recipe requires ingredients");
        }
        Map<String, ItemSpec> result = new HashMap<>();
        for (String key : section.getKeys(false)) {
            result.put(key, itemResolver.fromConfig(section.getConfigurationSection(key)));
        }
        return result;
    }

    private ForjadorOptions parseForjador(ConfigurationSection section) {
        if (section == null) {
            return new ForjadorOptions(0, false, false, null);
        }
        return new ForjadorOptions(
                section.getDouble("exp", 0),
                section.getBoolean("signature", false),
                section.getBoolean("modifiers", false),
                section.getString("modifier-pool")
        );
    }

    private NamespacedKey parseNamespacedKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        NamespacedKey parsed = NamespacedKey.fromString(raw.toLowerCase(Locale.ROOT));
        if (parsed != null) {
            return parsed;
        }
        return NamespacedKey.minecraft(raw.toLowerCase(Locale.ROOT));
    }

    private String sanitizeKey(String id) {
        return id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
    }

    private <T extends Enum<T>> T enumValue(Class<T> enumClass, String raw, T fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(enumClass, raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }
}
