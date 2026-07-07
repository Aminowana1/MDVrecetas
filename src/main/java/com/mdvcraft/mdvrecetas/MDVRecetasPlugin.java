package com.mdvcraft.mdvrecetas;

import com.mdvcraft.mdvrecetas.command.MDVRecetasCommand;
import com.mdvcraft.mdvrecetas.gui.RecipeGuiManager;
import com.mdvcraft.mdvrecetas.hook.MDVSocialHook;
import com.mdvcraft.mdvrecetas.hook.MMOItemsHook;
import com.mdvcraft.mdvrecetas.listener.CookingXpListener;
import com.mdvcraft.mdvrecetas.listener.RecipeCraftListener;
import com.mdvcraft.mdvrecetas.recipe.MdvRecipeManager;
import com.mdvcraft.mdvrecetas.service.FloatingTextService;
import com.mdvcraft.mdvrecetas.service.ForjadorXpService;
import com.mdvcraft.mdvrecetas.service.ItemResolver;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class MDVRecetasPlugin extends JavaPlugin {
    private MdvRecipeManager recipeManager;
    private ItemResolver itemResolver;
    private ForjadorXpService forjadorXpService;
    private FloatingTextService floatingTextService;
    private RecipeGuiManager recipeGuiManager;
    private MDVSocialHook mdvSocialHook;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveExamplesIfNeeded();

        MMOItemsHook mmoItemsHook = new MMOItemsHook();
        this.mdvSocialHook = new MDVSocialHook(this);
        this.itemResolver = new ItemResolver(mmoItemsHook);
        this.floatingTextService = new FloatingTextService(this);
        this.forjadorXpService = new ForjadorXpService(this, floatingTextService);
        this.recipeManager = new MdvRecipeManager(this, itemResolver);
        this.recipeGuiManager = new RecipeGuiManager(this, recipeManager, itemResolver, mdvSocialHook);

        int loaded = recipeManager.reloadRecipes();
        getLogger().info("MDVRecetas 0.3.1 enabled. Recipes: " + loaded);

        getServer().getPluginManager().registerEvents(new RecipeCraftListener(this, forjadorXpService), this);
        getServer().getPluginManager().registerEvents(new CookingXpListener(this, forjadorXpService), this);
        getServer().getPluginManager().registerEvents(recipeGuiManager, this);

        MDVRecetasCommand commandExecutor = new MDVRecetasCommand(this);
        PluginCommand command = getCommand("mdvrecetas");
        if (command != null) {
            command.setExecutor(commandExecutor);
            command.setTabCompleter(commandExecutor);
        }
    }

    @Override
    public void onDisable() {
        if (recipeGuiManager != null) {
            recipeGuiManager.closeAllAndReturnSearchItems();
        }
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

    public ForjadorXpService getForjadorXpService() {
        return forjadorXpService;
    }

    public RecipeGuiManager getRecipeGuiManager() {
        return recipeGuiManager;
    }

    public MDVSocialHook getMdvSocialHook() {
        return mdvSocialHook;
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
