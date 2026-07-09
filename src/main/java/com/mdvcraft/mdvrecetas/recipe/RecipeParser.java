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
        boolean hidden = section.getBoolean("hidden", section.getBoolean("hide", false));
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

        VisualOptions visual = parseVisualOptions(section);

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
                hidden,
                shape,
                shapedIngredients,
                shapelessIngredients,
                cookingIngredient,
                result,
                cookingTime,
                cookingVanillaExp,
                forjador,
                replaceVanilla,
                vanillaKey,
                visual.page(),
                visual.slot(),
                visual.group(),
                visual.primary(),
                visual.order()
        );
    }


    private VisualOptions parseVisualOptions(ConfigurationSection section) {
        int slot = firstInt(section, -1, "visual.slot", "display.slot", "viewer.slot", "category-view.slot");
        int page = firstInt(section, slot >= 0 ? 1 : -1, "visual.page", "display.page", "viewer.page", "category-view.page");
        if (slot < 0) {
            page = -1;
        } else if (page < 1) {
            page = 1;
        }
        String group = firstString(section, "", "visual.group", "display.group", "viewer.group", "category-view.group", "linked-group", "recipe-group", "group");
        boolean primary = firstBoolean(section, false, "visual.primary", "display.primary", "viewer.primary", "category-view.primary", "primary");
        int order = firstInt(section, 0, "visual.order", "display.order", "viewer.order", "category-view.order", "order");
        return new VisualOptions(page, slot, group == null ? "" : group.trim(), primary, order);
    }

    private int firstInt(ConfigurationSection section, int fallback, String... paths) {
        for (String path : paths) {
            if (section.contains(path)) {
                return section.getInt(path, fallback);
            }
        }
        return fallback;
    }

    private String firstString(ConfigurationSection section, String fallback, String... paths) {
        for (String path : paths) {
            if (section.contains(path)) {
                return section.getString(path, fallback);
            }
        }
        return fallback;
    }

    private boolean firstBoolean(ConfigurationSection section, boolean fallback, String... paths) {
        for (String path : paths) {
            if (section.contains(path)) {
                return section.getBoolean(path, fallback);
            }
        }
        return fallback;
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

    private record VisualOptions(int page, int slot, String group, boolean primary, int order) {
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
