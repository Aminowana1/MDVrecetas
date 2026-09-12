package com.mdvcraft.mdvrecetas.recipe;

import com.mdvcraft.mdvrecetas.MDVRecetasPlugin;
import com.mdvcraft.mdvrecetas.model.MdvRecipe;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Reads recipe YAML files and converts them into lightweight disk snapshots.
 * This class never touches Bukkit's recipe registry.
 */
final class RecipeDiskScanner {
    private final MDVRecetasPlugin plugin;
    private final RecipeParser parser;

    RecipeDiskScanner(MDVRecetasPlugin plugin, RecipeParser parser) {
        this.plugin = plugin;
        this.parser = parser;
    }

    Map<NamespacedKey, RecipeDiskEntry> scanAll() {
        File folder = recipeFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create recipes folder: " + folder.getAbsolutePath());
        }

        Map<NamespacedKey, RecipeDiskEntry> result = new LinkedHashMap<>();
        for (File file : listYamlFiles(folder)) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection recipesSection = yaml.getConfigurationSection("recipes");
            if (recipesSection == null) {
                continue;
            }

            for (String id : recipesSection.getKeys(false)) {
                RecipeDiskEntry entry = parseEntry(file, yaml, id);
                if (entry == null) {
                    continue;
                }
                NamespacedKey key = entry.recipe().getKey();
                if (result.containsKey(key)) {
                    plugin.getLogger().warning("Duplicate recipe key '" + key + "' in " + relativeRecipePath(file)
                            + "; keeping the first loaded definition.");
                    continue;
                }
                result.put(key, entry);
            }
        }
        return result;
    }

    RecipeDiskEntry scanOne(File file, String id) {
        if (file == null || id == null || id.isBlank() || !file.exists()) {
            return null;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        return parseEntry(file, yaml, id);
    }

    private RecipeDiskEntry parseEntry(File file, YamlConfiguration yaml, String id) {
        String path = "recipes." + id;
        ConfigurationSection section = yaml.getConfigurationSection(path);
        if (section == null || !section.getBoolean("enabled", true)) {
            return null;
        }
        try {
            MdvRecipe recipe = parser.parse(id, section);
            return new RecipeDiskEntry(recipe, relativeRecipePath(file), fingerprint(section));
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not load recipe '" + id + "' from " + file.getName() + ": " + exception.getMessage(),
                    exception);
            return null;
        }
    }

    private String fingerprint(ConfigurationSection section) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            List<String> paths = new ArrayList<>(section.getKeys(true));
            paths.sort(String.CASE_INSENSITIVE_ORDER);
            for (String path : paths) {
                if (section.isConfigurationSection(path)) {
                    continue;
                }
                Object value = section.get(path);
                update(digest, path);
                digest.update((byte) '=');
                update(digest, canonicalValue(value));
                digest.update((byte) '\n');
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            // SHA-256 is guaranteed by the JDK. This fallback keeps reloads safe
            // even on an unexpected runtime implementation.
            return Integer.toHexString(section.getValues(true).hashCode());
        }
    }

    private void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
    }

    private String canonicalValue(Object value) {
        if (value == null) {
            return "<null>";
        }
        if (value instanceof List<?> list) {
            StringBuilder builder = new StringBuilder("[");
            for (Object element : list) {
                builder.append(canonicalValue(element)).append('\u001f');
            }
            return builder.append(']').toString();
        }
        if (value instanceof Map<?, ?> map) {
            List<String> entries = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                entries.add(String.valueOf(entry.getKey()) + "=" + canonicalValue(entry.getValue()));
            }
            entries.sort(Comparator.naturalOrder());
            return "{" + String.join("\u001f", entries) + "}";
        }
        return value.getClass().getName() + ":" + value;
    }

    private File recipeFolder() {
        return new File(plugin.getDataFolder(), plugin.getConfig().getString("settings.recipe-folder", "recipes"));
    }

    private String relativeRecipePath(File file) {
        try {
            return recipeFolder().toPath().toAbsolutePath().normalize()
                    .relativize(file.toPath().toAbsolutePath().normalize())
                    .toString().replace(File.separatorChar, '/');
        } catch (Exception ignored) {
            return file.getName();
        }
    }

    private List<File> listYamlFiles(File folder) {
        if (folder == null || !folder.exists()) {
            return List.of();
        }
        File[] files = folder.listFiles();
        if (files == null) {
            return List.of();
        }
        List<File> result = new ArrayList<>();
        for (File file : files) {
            if (file.isDirectory()) {
                result.addAll(listYamlFiles(file));
            } else {
                String name = file.getName().toLowerCase(Locale.ROOT);
                if (name.endsWith(".yml") || name.endsWith(".yaml")) {
                    result.add(file);
                }
            }
        }
        result.sort(Comparator.comparing(File::getAbsolutePath));
        return result;
    }
}
