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
    private final Map<String, String> sourceFileByRecipeId = new HashMap<>();
    private final Set<NamespacedKey> registeredKeys = new HashSet<>();

    public MdvRecipeManager(MDVRecetasPlugin plugin, ItemResolver itemResolver) {
        this.plugin = plugin;
        this.itemResolver = itemResolver;
        this.parser = new RecipeParser(plugin, itemResolver);
    }

    public int reloadRecipes() {
        unregisterOwnRecipes();
        recipesByKey.clear();
        sourceFileByRecipeId.clear();
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


    public boolean recipeIdExists(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        for (MdvRecipe recipe : recipesByKey.values()) {
            if (recipe.getId().equalsIgnoreCase(id)) {
                return true;
            }
        }
        return false;
    }

    public boolean deleteRecipeFromFiles(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        File folder = new File(plugin.getDataFolder(), plugin.getConfig().getString("settings.recipe-folder", "recipes"));
        boolean deleted = false;
        for (File file : listYamlFiles(folder)) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            String path = "recipes." + id;
            if (yaml.contains(path)) {
                yaml.set(path, null);
                try {
                    yaml.save(file);
                    deleted = true;
                } catch (Exception exception) {
                    plugin.getLogger().warning("Could not delete recipe '" + id + "' from " + file.getName() + ": " + exception.getMessage());
                }
            }
        }
        return deleted;
    }


    public Optional<String> getSourceFileName(String recipeId) {
        if (recipeId == null || recipeId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(sourceFileByRecipeId.get(recipeId.toLowerCase(Locale.ROOT)));
    }

    public List<String> listRecipeFileNames() {
        File folder = recipeFolder();
        List<String> names = new ArrayList<>();
        for (File file : listYamlFiles(folder)) {
            names.add(relativeRecipePath(file));
        }
        String fallback = plugin.getConfig().getString("editor.default-save-file", "editor.yml");
        fallback = sanitizeRelativeRecipeFile(fallback);
        if (!names.contains(fallback)) {
            names.add(fallback);
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public File resolveRecipeFile(String relativeName) {
        File folder = recipeFolder();
        String safe = sanitizeRelativeRecipeFile(relativeName);
        File file = new File(folder, safe);
        try {
            String rootPath = folder.getCanonicalPath() + File.separator;
            String filePath = file.getCanonicalPath();
            if (!filePath.startsWith(rootPath)) {
                throw new IllegalArgumentException("Recipe file escapes recipe folder");
            }
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid recipe file: " + relativeName, exception);
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Could not create recipe folder: " + parent.getAbsolutePath());
        }
        return file;
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

    private String sanitizeRelativeRecipeFile(String raw) {
        String value = raw == null ? "editor.yml" : raw.trim().replace('\\', '/');
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        value = value.replace("..", "_");
        if (!value.toLowerCase(Locale.ROOT).endsWith(".yml") && !value.toLowerCase(Locale.ROOT).endsWith(".yaml")) {
            value += ".yml";
        }
        return value.isBlank() ? "editor.yml" : value;
    }

    public Optional<MdvRecipe> findMatchingCraftingRecipe(ItemStack[] matrix) {
        for (MdvRecipe recipe : recipesByKey.values()) {
            if (recipe.getStation() != StationType.CRAFTING_TABLE || recipe.getType() == RecipeType.COOKING) {
                continue;
            }
            if (matchesCraftingMatrix(recipe, matrix)) {
                return Optional.of(recipe);
            }
        }
        return Optional.empty();
    }

    private boolean matchesCraftingMatrix(MdvRecipe recipe, ItemStack[] rawMatrix) {
        ItemStack[] matrix = normalizeMatrix(rawMatrix);
        if (recipe.getType() == RecipeType.SHAPED) {
            for (int row = 0; row < 3; row++) {
                String line = row < recipe.getShape().size() ? recipe.getShape().get(row) : "";
                for (int col = 0; col < 3; col++) {
                    char symbol = col < line.length() ? line.charAt(col) : ' ';
                    ItemStack actual = matrix[row * 3 + col];
                    if (symbol == ' ') {
                        if (actual != null && !actual.getType().isAir()) {
                            return false;
                        }
                        continue;
                    }
                    ItemSpec expected = recipe.getShapedIngredients().get(symbol);
                    if (!itemResolver.matches(actual, expected) || actual.getAmount() < Math.max(1, expected.getAmount())) {
                        return false;
                    }
                }
            }
            return true;
        }

        List<ItemStack> actualItems = new ArrayList<>();
        for (ItemStack item : matrix) {
            if (item != null && !item.getType().isAir()) {
                actualItems.add(item);
            }
        }
        List<ItemSpec> expectedItems = new ArrayList<>();
        for (ItemSpec spec : recipe.getShapelessIngredients().values()) {
            for (int i = 0; i < Math.max(1, spec.getAmount()); i++) {
                expectedItems.add(spec);
            }
        }
        if (actualItems.size() != expectedItems.size()) {
            return false;
        }
        boolean[] used = new boolean[actualItems.size()];
        return matchShapeless(expectedItems, actualItems, used, 0);
    }

    private boolean matchShapeless(List<ItemSpec> expected, List<ItemStack> actual, boolean[] used, int index) {
        if (index >= expected.size()) {
            return true;
        }
        ItemSpec spec = expected.get(index);
        for (int i = 0; i < actual.size(); i++) {
            if (!used[i] && itemResolver.matches(actual.get(i), spec)) {
                used[i] = true;
                if (matchShapeless(expected, actual, used, index + 1)) {
                    return true;
                }
                used[i] = false;
            }
        }
        return false;
    }

    private ItemStack[] normalizeMatrix(ItemStack[] raw) {
        ItemStack[] result = new ItemStack[9];
        if (raw == null) {
            return result;
        }
        if (raw.length == 9) {
            System.arraycopy(raw, 0, result, 0, 9);
            return result;
        }
        if (raw.length == 4) {
            result[0] = raw[0];
            result[1] = raw[1];
            result[3] = raw[2];
            result[4] = raw[3];
        }
        return result;
    }

    public Optional<MdvRecipe> getByKey(NamespacedKey key) {
        return Optional.ofNullable(recipesByKey.get(key));
    }

    public Collection<MdvRecipe> getRecipes() {
        return Collections.unmodifiableCollection(recipesByKey.values());
    }

    public List<MdvRecipe> getVisibleRecipes() {
        List<MdvRecipe> result = new ArrayList<>();
        for (MdvRecipe recipe : recipesByKey.values()) {
            if (!recipe.isHidden()) {
                result.add(recipe);
            }
        }
        return result;
    }

    public List<MdvRecipe> getVisibleByCategory(String category) {
        if (category == null || category.isBlank()) {
            return getVisibleRecipes();
        }
        String normalized = category.toUpperCase(Locale.ROOT);
        List<MdvRecipe> result = new ArrayList<>();
        for (MdvRecipe recipe : recipesByKey.values()) {
            if (!recipe.isHidden() && normalized.equalsIgnoreCase(recipe.getCategory())) {
                result.add(recipe);
            }
        }
        return result;
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

    public List<MdvRecipe> collapseDisplayGroups(List<MdvRecipe> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, List<MdvRecipe>> grouped = new LinkedHashMap<>();
        List<MdvRecipe> standalone = new ArrayList<>();
        for (MdvRecipe recipe : input) {
            if (recipe == null) {
                continue;
            }
            if (!recipe.hasVisualGroup()) {
                standalone.add(recipe);
                continue;
            }
            grouped.computeIfAbsent(normalizeGroup(recipe.getVisualGroup()), ignored -> new ArrayList<>()).add(recipe);
        }
        List<MdvRecipe> result = new ArrayList<>(standalone);
        for (List<MdvRecipe> group : grouped.values()) {
            result.add(chooseDisplayRepresentative(group));
        }
        result.sort(displayComparator());
        return result;
    }

    public MdvRecipe displayRepresentative(MdvRecipe recipe) {
        if (recipe == null || !recipe.hasVisualGroup()) {
            return recipe;
        }
        List<MdvRecipe> visibleGroup = getLinkedRecipes(recipe, false);
        if (!visibleGroup.isEmpty()) {
            return chooseDisplayRepresentative(visibleGroup);
        }
        return chooseDisplayRepresentative(getLinkedRecipes(recipe, true));
    }

    public List<MdvRecipe> getLinkedRecipes(MdvRecipe recipe) {
        return getLinkedRecipes(recipe, false);
    }

    public List<MdvRecipe> getLinkedRecipes(MdvRecipe recipe, boolean includeHidden) {
        if (recipe == null || !recipe.hasVisualGroup()) {
            return recipe == null ? List.of() : List.of(recipe);
        }
        String group = normalizeGroup(recipe.getVisualGroup());
        List<MdvRecipe> result = new ArrayList<>();
        for (MdvRecipe candidate : recipesByKey.values()) {
            if (!includeHidden && candidate.isHidden()) {
                continue;
            }
            if (candidate.hasVisualGroup() && normalizeGroup(candidate.getVisualGroup()).equals(group)) {
                result.add(candidate);
            }
        }
        if (result.isEmpty()) {
            result.add(recipe);
        }
        result.sort(displayComparator());
        return result;
    }

    private MdvRecipe chooseDisplayRepresentative(List<MdvRecipe> group) {
        if (group == null || group.isEmpty()) {
            return null;
        }
        return group.stream()
                .min(Comparator
                        .comparing((MdvRecipe recipe) -> !recipe.isVisualPrimary())
                        .thenComparing(recipe -> !recipe.hasVisualPosition())
                        .thenComparingInt(MdvRecipe::getVisualOrder)
                        .thenComparing(MdvRecipe::getId, String.CASE_INSENSITIVE_ORDER))
                .orElse(group.get(0));
    }

    public Comparator<MdvRecipe> displayComparator() {
        return Comparator
                .comparingInt(MdvRecipe::getVisualOrder)
                .thenComparing(MdvRecipe::getId, String.CASE_INSENSITIVE_ORDER);
    }

    private String normalizeGroup(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
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

    public List<MdvRecipe> findVisibleRecipesUsing(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return List.of();
        }
        List<MdvRecipe> result = new ArrayList<>();
        for (MdvRecipe recipe : recipesByKey.values()) {
            if (!recipe.isHidden() && usesIngredient(recipe, itemStack)) {
                result.add(recipe);
            }
        }
        return result;
    }

    public List<MdvRecipe> findVisibleDisplayRecipesUsing(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return List.of();
        }
        List<MdvRecipe> result = new ArrayList<>();
        for (MdvRecipe recipe : recipesByKey.values()) {
            if (!usesIngredient(recipe, itemStack)) {
                continue;
            }
            MdvRecipe representative = displayRepresentative(recipe);
            if (representative != null && !representative.isHidden()) {
                result.add(representative);
            } else if (!recipe.isHidden()) {
                result.add(recipe);
            }
        }
        return collapseDisplayGroups(result);
    }

    public Optional<MdvRecipe> findVisibleRecipeProducing(ItemSpec itemSpec) {
        if (itemSpec == null) {
            return Optional.empty();
        }
        for (MdvRecipe recipe : recipesByKey.values()) {
            if (recipe.isHidden()) {
                continue;
            }
            ItemStack result = itemResolver.buildItem(recipe.getResult());
            if (result == null || result.getType().isAir()) {
                continue;
            }
            if (itemResolver.matches(result, itemSpec)) {
                return Optional.of(displayRepresentative(recipe));
            }
        }
        return Optional.empty();
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

    public Optional<MdvRecipe> findCookingRecipe(Block block, ItemStack source) {
        StationType station = stationFromBlock(block == null ? null : block.getType());
        if (station == null) {
            return Optional.empty();
        }
        for (MdvRecipe recipe : recipesByKey.values()) {
            if (recipe.getType() != RecipeType.COOKING || recipe.getStation() != station) {
                continue;
            }
            if (itemResolver.matches(source, recipe.getCookingIngredient())) {
                return Optional.of(recipe);
            }
        }
        return Optional.empty();
    }

    public boolean hasCookingRecipeWithInputMaterial(Block block, ItemStack source) {
        StationType station = stationFromBlock(block == null ? null : block.getType());
        if (station == null || source == null || source.getType().isAir()) {
            return false;
        }
        for (MdvRecipe recipe : recipesByKey.values()) {
            if (recipe.getType() != RecipeType.COOKING || recipe.getStation() != station) {
                continue;
            }
            ItemStack expected = itemResolver.buildItem(recipe.getCookingIngredient());
            if (expected != null && !expected.getType().isAir() && expected.getType() == source.getType()) {
                return true;
            }
        }
        return false;
    }

    public Optional<MdvRecipe> findCookingRecipe(Block block, ItemStack source, ItemStack result) {
        Optional<MdvRecipe> match = findCookingRecipe(block, source);
        if (match.isEmpty()) {
            return Optional.empty();
        }
        MdvRecipe recipe = match.get();
        ItemStack recipeResult = itemResolver.buildItem(recipe.getResult());
        if (recipeResult == null) {
            return Optional.empty();
        }
        recipeResult.setAmount(result == null ? 1 : result.getAmount());
        if (result != null && result.isSimilar(recipeResult)) {
            return Optional.of(recipe);
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
                sourceFileByRecipeId.put(id.toLowerCase(Locale.ROOT), relativeRecipePath(file));
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
        RecipeChoice choice = itemResolver.buildCookingChoice(recipe.getCookingIngredient());
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
