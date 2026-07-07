package com.mdvcraft.mdvrecetas.recipe;

import com.mdvcraft.mdvrecetas.MDVRecetasPlugin;
import com.mdvcraft.mdvrecetas.model.ItemSpec;
import com.mdvcraft.mdvrecetas.model.MdvRecipe;
import com.mdvcraft.mdvrecetas.model.RecipeType;
import com.mdvcraft.mdvrecetas.model.StationType;
import com.mdvcraft.mdvrecetas.service.ItemResolver;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.*;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

public final class MdvRecipeManager {
    private final MDVRecetasPlugin plugin;
    private final ItemResolver itemResolver;
    private final RecipeParser parser;
    private final Map<NamespacedKey, MdvRecipe> recipesByKey = new LinkedHashMap<>();
    private final Set<NamespacedKey> registeredKeys = new HashSet<>();

    public MdvRecipeManager(MDVRecetasPlugin plugin, ItemResolver itemResolver) {
        this.plugin = plugin;
        this.itemResolver = itemResolver;
        this.parser = new RecipeParser(plugin, itemResolver);
    }

    public int reloadRecipes() {
        unregisterOwnRecipes();
        recipesByKey.clear();
        registeredKeys.clear();

        File folder = new File(plugin.getDataFolder(), plugin.getConfig().getString("settings.recipe-folder", "recipes"));
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create recipes folder: " + folder.getAbsolutePath());
        }

        List<File> files = listYamlFiles(folder);
        int loaded = 0;
        for (File file : files) {
            loaded += loadFile(file);
        }
        plugin.getLogger().info("Registered " + loaded + " MDVRecetas recipes.");
        return loaded;
    }

    public Optional<MdvRecipe> getByKey(NamespacedKey key) {
        return Optional.ofNullable(recipesByKey.get(key));
    }

    public Collection<MdvRecipe> getRecipes() {
        return Collections.unmodifiableCollection(recipesByKey.values());
    }


    public List<MdvRecipe> getByCategory(String category) {
        if (category == null || category.isBlank()) {
            return new ArrayList<>(recipesByKey.values());
        }
        String normalized = category.toUpperCase(Locale.ROOT);
        List<MdvRecipe> result = new ArrayList<>();
        for (MdvRecipe recipe : recipesByKey.values()) {
            if (normalized.equalsIgnoreCase(recipe.getCategory())) {
                result.add(recipe);
            }
        }
        return result;
    }

    public List<MdvRecipe> findRecipesUsing(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return List.of();
        }
        List<MdvRecipe> result = new ArrayList<>();
        for (MdvRecipe recipe : recipesByKey.values()) {
            if (usesIngredient(recipe, itemStack)) {
                result.add(recipe);
            }
        }
        return result;
    }

    private boolean usesIngredient(MdvRecipe recipe, ItemStack itemStack) {
        if (recipe.getType() == RecipeType.SHAPED) {
            for (ItemSpec spec : recipe.getShapedIngredients().values()) {
                if (itemResolver.matches(itemStack, spec)) {
                    return true;
                }
            }
            return false;
        }
        if (recipe.getType() == RecipeType.SHAPELESS) {
            for (ItemSpec spec : recipe.getShapelessIngredients().values()) {
                if (itemResolver.matches(itemStack, spec)) {
                    return true;
                }
            }
            return false;
        }
        return itemResolver.matches(itemStack, recipe.getCookingIngredient());
    }

    public Optional<MdvRecipe> findCookingRecipe(Block block, ItemStack source, ItemStack result) {
        StationType station = stationFromBlock(block == null ? null : block.getType());
        if (station == null) {
            return Optional.empty();
        }
        for (MdvRecipe recipe : recipesByKey.values()) {
            if (recipe.getType() != RecipeType.COOKING || recipe.getStation() != station) {
                continue;
            }
            if (!itemResolver.matches(source, recipe.getCookingIngredient())) {
                continue;
            }
            ItemStack recipeResult = itemResolver.buildItem(recipe.getResult());
            if (recipeResult == null) {
                continue;
            }
            recipeResult.setAmount(result == null ? 1 : result.getAmount());
            if (result != null && result.isSimilar(recipeResult)) {
                return Optional.of(recipe);
            }
        }
        return Optional.empty();
    }

    public void unregisterOwnRecipes() {
        Iterator<Recipe> iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            if (recipe instanceof Keyed keyed && keyed.getKey().getNamespace().equalsIgnoreCase(plugin.getName().toLowerCase(Locale.ROOT))) {
                iterator.remove();
            }
        }
        for (NamespacedKey key : registeredKeys) {
            Bukkit.removeRecipe(key);
        }
        registeredKeys.clear();
    }

    private int loadFile(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection recipesSection = yaml.getConfigurationSection("recipes");
        if (recipesSection == null) {
            return 0;
        }

        int loaded = 0;
        for (String id : recipesSection.getKeys(false)) {
            ConfigurationSection section = recipesSection.getConfigurationSection(id);
            if (section == null || !section.getBoolean("enabled", true)) {
                continue;
            }
            try {
                MdvRecipe recipe = parser.parse(id, section);
                register(recipe);
                loaded++;
            } catch (Exception exception) {
                plugin.getLogger().log(Level.WARNING, "Could not load recipe '" + id + "' from " + file.getName() + ": " + exception.getMessage(), exception);
            }
        }
        return loaded;
    }

    private void register(MdvRecipe recipe) {
        if (recipe.isReplaceVanilla() && recipe.getVanillaKey() != null) {
            Bukkit.removeRecipe(recipe.getVanillaKey());
        }

        ItemStack result = itemResolver.buildItem(recipe.getResult());
        if (result == null || result.getType().isAir()) {
            throw new IllegalArgumentException("Result could not be built. Check item kind/type/id/data.");
        }

        Recipe bukkitRecipe = switch (recipe.getType()) {
            case SHAPED -> buildShapedRecipe(recipe, result);
            case SHAPELESS -> buildShapelessRecipe(recipe, result);
            case COOKING -> buildCookingRecipe(recipe, result);
        };

        if (!Bukkit.addRecipe(bukkitRecipe)) {
            throw new IllegalStateException("Bukkit rejected recipe " + recipe.getKey());
        }
        registeredKeys.add(recipe.getKey());
        recipesByKey.put(recipe.getKey(), recipe);
    }

    private Recipe buildShapedRecipe(MdvRecipe recipe, ItemStack result) {
        ShapedRecipe shapedRecipe = new ShapedRecipe(recipe.getKey(), result);
        shapedRecipe.shape(recipe.getShape().toArray(new String[0]));
        for (Map.Entry<Character, ItemSpec> entry : recipe.getShapedIngredients().entrySet()) {
            RecipeChoice choice = itemResolver.buildChoice(entry.getValue());
            if (choice == null) {
                throw new IllegalArgumentException("Could not build ingredient choice for symbol " + entry.getKey());
            }
            shapedRecipe.setIngredient(entry.getKey(), choice);
        }
        return shapedRecipe;
    }

    private Recipe buildShapelessRecipe(MdvRecipe recipe, ItemStack result) {
        ShapelessRecipe shapelessRecipe = new ShapelessRecipe(recipe.getKey(), result);
        for (ItemSpec spec : recipe.getShapelessIngredients().values()) {
            RecipeChoice choice = itemResolver.buildChoice(spec);
            if (choice == null) {
                throw new IllegalArgumentException("Could not build shapeless ingredient choice");
            }
            int amount = Math.max(1, spec.getAmount());
            for (int i = 0; i < amount; i++) {
                shapelessRecipe.addIngredient(choice);
            }
        }
        return shapelessRecipe;
    }

    private Recipe buildCookingRecipe(MdvRecipe recipe, ItemStack result) {
        RecipeChoice choice = itemResolver.buildChoice(recipe.getCookingIngredient());
        if (choice == null) {
            throw new IllegalArgumentException("Could not build cooking ingredient choice");
        }
        return switch (recipe.getStation()) {
            case FURNACE -> new FurnaceRecipe(recipe.getKey(), result, choice, recipe.getCookingVanillaExp(), recipe.getCookingTime());
            case BLAST_FURNACE -> new BlastingRecipe(recipe.getKey(), result, choice, recipe.getCookingVanillaExp(), recipe.getCookingTime());
            case SMOKER -> new SmokingRecipe(recipe.getKey(), result, choice, recipe.getCookingVanillaExp(), recipe.getCookingTime());
            case CAMPFIRE -> new CampfireRecipe(recipe.getKey(), result, choice, recipe.getCookingVanillaExp(), recipe.getCookingTime());
            default -> throw new IllegalArgumentException("Station " + recipe.getStation() + " is not a cooking station");
        };
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
            } else if (file.getName().endsWith(".yml") || file.getName().endsWith(".yaml")) {
                result.add(file);
            }
        }
        result.sort(Comparator.comparing(File::getAbsolutePath));
        return result;
    }

    private StationType stationFromBlock(Material material) {
        if (material == null) {
            return null;
        }
        return switch (material) {
            case FURNACE -> StationType.FURNACE;
            case BLAST_FURNACE -> StationType.BLAST_FURNACE;
            case SMOKER -> StationType.SMOKER;
            default -> null;
        };
    }
}
