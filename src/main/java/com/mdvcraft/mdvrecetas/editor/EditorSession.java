package com.mdvcraft.mdvrecetas.editor;

import com.mdvcraft.mdvrecetas.model.MdvRecipe;
import com.mdvcraft.mdvrecetas.model.RecipeType;
import com.mdvcraft.mdvrecetas.model.StationType;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public final class EditorSession {
    private StationType station = StationType.CRAFTING_TABLE;
    private RecipeType recipeType = RecipeType.SHAPED;
    private String category = "MATERIALES";
    private boolean hidden = false;
    private int cookingTime = 200;
    private float vanillaExp = 0.0F;
    private double forjadorExp = 0.0D;
    private String customRecipeId;
    private String editingRecipeId;
    private MdvRecipe originalRecipe;
    private boolean replaceVanilla = false;
    private String vanillaKey = "";
    private final Map<Integer, ItemStack> items = new HashMap<>();

    public StationType getStation() {
        return station;
    }

    public void setStation(StationType station) {
        this.station = station;
        if (station != null && station.isCookingStation()) {
            this.recipeType = RecipeType.COOKING;
        }
    }

    public RecipeType getRecipeType() {
        return recipeType;
    }

    public void setRecipeType(RecipeType recipeType) {
        this.recipeType = recipeType;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public int getCookingTime() {
        return cookingTime;
    }

    public void setCookingTime(int cookingTime) {
        this.cookingTime = Math.max(20, cookingTime);
    }

    public float getVanillaExp() {
        return vanillaExp;
    }

    public void setVanillaExp(float vanillaExp) {
        this.vanillaExp = Math.max(0.0F, vanillaExp);
    }

    public double getForjadorExp() {
        return forjadorExp;
    }

    public void setForjadorExp(double forjadorExp) {
        this.forjadorExp = Math.max(0.0D, forjadorExp);
    }

    public String getCustomRecipeId() {
        return customRecipeId;
    }

    public void setCustomRecipeId(String customRecipeId) {
        this.customRecipeId = customRecipeId;
    }

    public String getEditingRecipeId() {
        return editingRecipeId;
    }

    public void setEditingRecipeId(String editingRecipeId) {
        this.editingRecipeId = editingRecipeId;
    }

    public boolean isEditing() {
        return editingRecipeId != null && !editingRecipeId.isBlank();
    }

    public MdvRecipe getOriginalRecipe() {
        return originalRecipe;
    }

    public void setOriginalRecipe(MdvRecipe originalRecipe) {
        this.originalRecipe = originalRecipe;
    }

    public boolean isReplaceVanilla() {
        return replaceVanilla;
    }

    public void setReplaceVanilla(boolean replaceVanilla) {
        this.replaceVanilla = replaceVanilla;
    }

    public String getVanillaKey() {
        return vanillaKey;
    }

    public void setVanillaKey(String vanillaKey) {
        this.vanillaKey = vanillaKey == null ? "" : vanillaKey;
    }

    public Map<Integer, ItemStack> getItems() {
        return items;
    }

    public void clearItems() {
        items.clear();
    }

    public void clearEditing() {
        editingRecipeId = null;
        originalRecipe = null;
        customRecipeId = null;
    }

    public void resetOptions() {
        this.recipeType = station != null && station.isCookingStation() ? RecipeType.COOKING : RecipeType.SHAPED;
        this.category = "MATERIALES";
        this.hidden = false;
        this.cookingTime = 200;
        this.vanillaExp = 0.0F;
        this.forjadorExp = 0.0D;
        this.replaceVanilla = false;
        this.vanillaKey = "";
        this.customRecipeId = null;
        this.editingRecipeId = null;
        this.originalRecipe = null;
    }
}
