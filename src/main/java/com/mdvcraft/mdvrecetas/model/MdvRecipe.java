package com.mdvcraft.mdvrecetas.model;

import org.bukkit.NamespacedKey;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class MdvRecipe {
    private final String id;
    private final NamespacedKey key;
    private final StationType station;
    private final RecipeType type;
    private final String category;
    private final boolean hidden;
    private final List<String> shape;
    private final Map<Character, ItemSpec> shapedIngredients;
    private final Map<String, ItemSpec> shapelessIngredients;
    private final ItemSpec cookingIngredient;
    private final ItemSpec result;
    private final int cookingTime;
    private final float cookingVanillaExp;
    private final ForjadorOptions forjador;
    private final boolean replaceVanilla;
    private final NamespacedKey vanillaKey;

    public MdvRecipe(
            String id,
            NamespacedKey key,
            StationType station,
            RecipeType type,
            String category,
            boolean hidden,
            List<String> shape,
            Map<Character, ItemSpec> shapedIngredients,
            Map<String, ItemSpec> shapelessIngredients,
            ItemSpec cookingIngredient,
            ItemSpec result,
            int cookingTime,
            float cookingVanillaExp,
            ForjadorOptions forjador,
            boolean replaceVanilla,
            NamespacedKey vanillaKey
    ) {
        this.id = id;
        this.key = key;
        this.station = station;
        this.type = type;
        this.category = category;
        this.hidden = hidden;
        this.shape = shape == null ? List.of() : List.copyOf(shape);
        this.shapedIngredients = shapedIngredients == null ? Map.of() : Map.copyOf(shapedIngredients);
        this.shapelessIngredients = shapelessIngredients == null ? Map.of() : Map.copyOf(shapelessIngredients);
        this.cookingIngredient = cookingIngredient;
        this.result = result;
        this.cookingTime = cookingTime;
        this.cookingVanillaExp = cookingVanillaExp;
        this.forjador = forjador;
        this.replaceVanilla = replaceVanilla;
        this.vanillaKey = vanillaKey;
    }

    public String getId() {
        return id;
    }

    public NamespacedKey getKey() {
        return key;
    }

    public StationType getStation() {
        return station;
    }

    public RecipeType getType() {
        return type;
    }

    public String getCategory() {
        return category;
    }

    public boolean isHidden() {
        return hidden;
    }

    public List<String> getShape() {
        return Collections.unmodifiableList(shape);
    }

    public Map<Character, ItemSpec> getShapedIngredients() {
        return Collections.unmodifiableMap(shapedIngredients);
    }

    public Map<String, ItemSpec> getShapelessIngredients() {
        return Collections.unmodifiableMap(shapelessIngredients);
    }

    public ItemSpec getCookingIngredient() {
        return cookingIngredient;
    }

    public ItemSpec getResult() {
        return result;
    }

    public int getCookingTime() {
        return cookingTime;
    }

    public float getCookingVanillaExp() {
        return cookingVanillaExp;
    }

    public ForjadorOptions getForjador() {
        return forjador;
    }

    public boolean isReplaceVanilla() {
        return replaceVanilla;
    }

    public NamespacedKey getVanillaKey() {
        return vanillaKey;
    }
}
