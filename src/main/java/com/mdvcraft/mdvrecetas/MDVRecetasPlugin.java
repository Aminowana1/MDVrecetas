package com.mdvcraft.mdvrecetas;

import com.mdvcraft.mdvrecetas.command.MDVRecetasCommand;
import com.mdvcraft.mdvrecetas.hook.MMOItemsHook;
import com.mdvcraft.mdvrecetas.listener.CookingXpListener;
import com.mdvcraft.mdvrecetas.listener.RecipeCraftListener;
import com.mdvcraft.mdvrecetas.recipe.MdvRecipeManager;
import com.mdvcraft.mdvrecetas.service.ForjadorXpService;
import com.mdvcraft.mdvrecetas.service.ItemResolver;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class MDVRecetasPlugin extends JavaPlugin {
    private MdvRecipeManager recipeManager;
    private ItemResolver itemResolver;
    private ForjadorXpService forjadorXpService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveExamplesIfNeeded();

        MMOItemsHook mmoItemsHook = new MMOItemsHook();
        this.itemResolver = new ItemResolver(mmoItemsHook);
        this.forjadorXpService = new ForjadorXpService(this);
        this.recipeManager = new MdvRecipeManager(this, itemResolver);

        int loaded = recipeManager.reloadRecipes();
        getLogger().info("MDVRecetas 0.1.0 enabled. Recipes: " + loaded);

        getServer().getPluginManager().registerEvents(new RecipeCraftListener(this, forjadorXpService), this);
        getServer().getPluginManager().registerEvents(new CookingXpListener(this, forjadorXpService), this);

        MDVRecetasCommand commandExecutor = new MDVRecetasCommand(this);
        PluginCommand command = getCommand("mdvrecetas");
        if (command != null) {
            command.setExecutor(commandExecutor);
            command.setTabCompleter(commandExecutor);
        }
    }

    @Override
    public void onDisable() {
        if (recipeManager != null) {
            recipeManager.unregisterOwnRecipes();
        }
    }

    public MdvRecipeManager getRecipeManager() {
        return recipeManager;
    }

    public ItemResolver getItemResolver() {
        return itemResolver;
    }

    private void saveExamplesIfNeeded() {
        File recipesFolder = new File(getDataFolder(), "recipes");
        if (!recipesFolder.exists()) {
            recipesFolder.mkdirs();
        }
        File examples = new File(recipesFolder, "examples.yml");
        if (!examples.exists()) {
            saveResource("recipes/examples.yml", false);
        }
    }
}
