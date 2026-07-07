package com.mdvcraft.mdvrecetas.gui;

import com.mdvcraft.mdvrecetas.model.MdvRecipe;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public final class RecipeMenuHolder implements InventoryHolder {
    public enum Screen {
        MAIN,
        RECIPE
    }

    private Inventory inventory;
    private final Screen screen;
    private String category;
    private int page;
    private final MdvRecipe recipe;
    private final Map<Integer, MdvRecipe> recipeSlots = new HashMap<>();

    public RecipeMenuHolder(Screen screen, String category, int page, MdvRecipe recipe) {
        this.screen = screen;
        this.category = category;
        this.page = page;
        this.recipe = recipe;
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

    public Map<Integer, MdvRecipe> getRecipeSlots() {
        return recipeSlots;
    }

    public void clearRecipeSlots() {
        recipeSlots.clear();
    }
}
