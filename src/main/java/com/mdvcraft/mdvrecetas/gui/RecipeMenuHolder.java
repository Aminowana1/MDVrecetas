package com.mdvcraft.mdvrecetas.gui;

import com.mdvcraft.mdvrecetas.model.ItemSpec;
import com.mdvcraft.mdvrecetas.model.MdvRecipe;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RecipeMenuHolder implements InventoryHolder {
    public enum Screen {
        MAIN,
        CATEGORY,
        SEARCH,
        RECIPE
    }

    public enum BackTarget {
        MAIN,
        CATEGORY,
        SEARCH,
        RECIPE,
        MDVSOCIAL
    }

    private Inventory inventory;
    private Screen screen;
    private String category;
    private int page;
    private MdvRecipe recipe;
    private BackTarget backTarget;
    private MdvRecipe parentRecipe;
    private int parentPage;
    private final Map<Integer, MdvRecipe> recipeSlots = new HashMap<>();
    private final Map<Integer, ItemSpec> ingredientSlots = new HashMap<>();
    private boolean adminMode;
    private List<RecipeBackState> recipeBackStack = new ArrayList<>();

    public RecipeMenuHolder(Screen screen, String category, int page, MdvRecipe recipe, BackTarget backTarget) {
        this.screen = screen;
        this.category = category;
        this.page = page;
        this.recipe = recipe;
        this.backTarget = backTarget;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public Screen getScreen() {
        return screen;
    }

    public void setScreen(Screen screen) {
        this.screen = screen;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public MdvRecipe getRecipe() {
        return recipe;
    }

    public void setRecipe(MdvRecipe recipe) {
        this.recipe = recipe;
    }

    public BackTarget getBackTarget() {
        return backTarget;
    }

    public void setBackTarget(BackTarget backTarget) {
        this.backTarget = backTarget;
    }

    public MdvRecipe getParentRecipe() {
        return parentRecipe;
    }

    public void setParentRecipe(MdvRecipe parentRecipe) {
        this.parentRecipe = parentRecipe;
    }

    public int getParentPage() {
        return parentPage;
    }

    public void setParentPage(int parentPage) {
        this.parentPage = parentPage;
    }

    public Map<Integer, MdvRecipe> getRecipeSlots() {
        return recipeSlots;
    }

    public Map<Integer, ItemSpec> getIngredientSlots() {
        return ingredientSlots;
    }

    public boolean isAdminMode() {
        return adminMode;
    }

    public void setAdminMode(boolean adminMode) {
        this.adminMode = adminMode;
    }

    public List<RecipeBackState> getRecipeBackStack() {
        return recipeBackStack;
    }

    public void setRecipeBackStack(List<RecipeBackState> recipeBackStack) {
        this.recipeBackStack = recipeBackStack == null ? new ArrayList<>() : new ArrayList<>(recipeBackStack);
    }

    public void clearRecipeSlots() {
        recipeSlots.clear();
    }

    public void clearIngredientSlots() {
        ingredientSlots.clear();
    }

    public record RecipeBackState(MdvRecipe recipe, String category, int page, BackTarget backTarget) {
    }
}
