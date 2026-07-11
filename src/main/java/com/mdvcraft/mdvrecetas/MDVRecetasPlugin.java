package com.mdvcraft.mdvrecetas;

import com.mdvcraft.mdvrecetas.command.MDVRecetasCommand;
import com.mdvcraft.mdvrecetas.gui.RecipeGuiManager;
import com.mdvcraft.mdvrecetas.editor.EditorGuiManager;
import com.mdvcraft.mdvrecetas.hook.MDVSocialHook;
import com.mdvcraft.mdvrecetas.hook.MMOItemsHook;
import com.mdvcraft.mdvrecetas.listener.CookingXpListener;
import com.mdvcraft.mdvrecetas.listener.RecipeCraftListener;
import com.mdvcraft.mdvrecetas.recipe.MdvRecipeManager;
import com.mdvcraft.mdvrecetas.service.FloatingTextService;
import com.mdvcraft.mdvrecetas.service.ForjadorXpService;
import com.mdvcraft.mdvrecetas.service.ItemResolver;
import com.mdvcraft.mdvrecetas.service.RecipeSignatureService;
import com.mdvcraft.mdvrecetas.service.ForjadorModifierService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class MDVRecetasPlugin extends JavaPlugin {
    private MdvRecipeManager recipeManager;
    private ItemResolver itemResolver;
    private ForjadorXpService forjadorXpService;
    private FloatingTextService floatingTextService;
    private RecipeSignatureService recipeSignatureService;
    private ForjadorModifierService forjadorModifierService;
    private RecipeGuiManager recipeGuiManager;
    private EditorGuiManager editorGuiManager;
    private MDVSocialHook mdvSocialHook;
    private com.mdvcraft.mdvrecetas.placeholder.MDVRecetasPlaceholderExpansion placeholderExpansion;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveModifiersIfNeeded();
        saveExamplesIfNeeded();

        MMOItemsHook mmoItemsHook = new MMOItemsHook();
        this.mdvSocialHook = new MDVSocialHook(this);
        this.itemResolver = new ItemResolver(mmoItemsHook);
        this.floatingTextService = new FloatingTextService(this);
        this.forjadorXpService = new ForjadorXpService(this, floatingTextService);
        this.recipeSignatureService = new RecipeSignatureService(this);
        this.forjadorModifierService = new ForjadorModifierService(this);
        this.recipeManager = new MdvRecipeManager(this, itemResolver);
        this.recipeGuiManager = new RecipeGuiManager(this, recipeManager, itemResolver, mdvSocialHook);
        this.editorGuiManager = new EditorGuiManager(this, recipeManager, itemResolver, mdvSocialHook);

        int loaded = recipeManager.reloadRecipes();
        getLogger().info("MDVRecetas 0.6.7 enabled. Recipes: " + loaded);

        getServer().getPluginManager().registerEvents(new RecipeCraftListener(this, forjadorXpService, recipeSignatureService, forjadorModifierService), this);
        getServer().getPluginManager().registerEvents(new CookingXpListener(this, forjadorXpService, recipeSignatureService, forjadorModifierService), this);
        getServer().getPluginManager().registerEvents(recipeGuiManager, this);
        getServer().getPluginManager().registerEvents(editorGuiManager, this);

        registerPlaceholdersIfAvailable();

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
        if (editorGuiManager != null) {
            editorGuiManager.closeAllAndReturnEditorItems();
        }
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
            placeholderExpansion = null;
        }
        if (recipeManager != null) {
            recipeManager.unregisterOwnRecipes();
        }
    }


    private void registerPlaceholdersIfAvailable() {
        if (!getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        try {
            this.placeholderExpansion = new com.mdvcraft.mdvrecetas.placeholder.MDVRecetasPlaceholderExpansion(this);
            if (this.placeholderExpansion.register()) {
                getLogger().info("PlaceholderAPI placeholders registered.");
            }
        } catch (Throwable throwable) {
            getLogger().warning("Could not register PlaceholderAPI placeholders: " + throwable.getMessage());
            this.placeholderExpansion = null;
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

    public RecipeSignatureService getRecipeSignatureService() {
        return recipeSignatureService;
    }

    public ForjadorModifierService getForjadorModifierService() {
        return forjadorModifierService;
    }

    public RecipeGuiManager getRecipeGuiManager() {
        return recipeGuiManager;
    }

    public EditorGuiManager getEditorGuiManager() {
        return editorGuiManager;
    }

    public MDVSocialHook getMdvSocialHook() {
        return mdvSocialHook;
    }

    private void saveModifiersIfNeeded() {
        File modifiers = new File(getDataFolder(), "modifiers.yml");
        if (!modifiers.exists()) {
            try {
                saveResource("modifiers.yml", false);
            } catch (IllegalArgumentException ex) {
                // Fallback defensivo: si el jar fue compilado sin incluir modifiers.yml,
                // lo generamos igual para no dejar la carpeta incompleta.
                try {
                    if (!getDataFolder().exists()) {
                        getDataFolder().mkdirs();
                    }
                    Files.writeString(modifiers.toPath(), DEFAULT_MODIFIERS_YML, StandardCharsets.UTF_8);
                    getLogger().warning("No se encontro modifiers.yml dentro del jar; se genero uno por fallback interno.");
                } catch (IOException ioException) {
                    getLogger().severe("No se pudo crear modifiers.yml: " + ioException.getMessage());
                }
            }
        }
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
    private static final String DEFAULT_MODIFIERS_YML = """
# ==========================================================
# Modificadores por Forjador
#
# Este archivo separa la tabla de modificadores de config.yml.
# MDVRecetas NO usa las probabilidades internas de MMOItems.
# Solo toma la lista de modificadores que el item acepta y luego
# elige uno usando estas calidades y el nivel de Forjador.
#
# Para activar en una receta:
# forjador:
#   modifiers: true
#
# Bloqueados: nunca salen por crafteo normal.
# ==========================================================
forjador-modifiers:
  enabled: true

  level:
    min: 1
    max: 50
    # ID interno de la profesion en MMOCore.
    profession-id: forjador
    # Fallback por PlaceholderAPI. MDVRecetas intenta primero leer MMOCore por API directa.
    placeholder: '%mmocore_profession_forjador%'

  chances:
    # Nivel 1: muchas chances de malo/normal, poco bueno.
    level-1:
      bad: 40
      normal: 50
      good: 9
      very-good: 1

    # Nivel 50: sigue sin garantizar perfecto, pero mejora mucho.
    level-50:
      bad: 5
      normal: 45
      good: 35
      very-good: 15

  # Si un modificador existe en el item pero no está en ninguna lista,
  # por defecto NO sale. Puedes poner normal/bad/good/very-good si quieres.
  unclassified-as: none

  # Además de IDs exactos bloqueados, cualquier ID que contenga estas palabras se bloquea.
  blocked-contains:
    - reliquia
    - simbionte

  default-weight:
    bad: 1
    normal: 1
    good: 1
    very-good: 1

  # Pesos opcionales por modificador concreto. Si no aparece aquí, usa default-weight.
  weights: {}

  # Opcional: si MDVRecetas no puede leer automaticamente el prefix desde MMOItems,
  # puedes forzarlo aqui. Normalmente puedes dejarlo vacio.
  # Ejemplo:
  # prefix-overrides:
  #   t1_tanque_oxidado: '&8Oxidado'
  prefix-overrides:
    # TANQUE
    t1_tanque_oxidado: '&8Oxidado'
    t1_tanque_abollado: '&7Abollado'
    t1_tanque_gastado: '&7Gastado'
    t1_tanque_pulido: '&fPulido'
    t1_tanque_resistente: '&aResistente'
    t1_tanque_reforzado: '&2Reforzado'

    # RANGO
    t1_rango_torcido: '&8Torcido'
    t1_rango_mal_ajustado: '&7Mal Ajustado'
    t1_rango_gastado: '&7Gastado'
    t1_rango_estable: '&fEstable'
    t1_rango_certero: '&aCertero'
    t1_rango_preciso: '&2Preciso'

    # MAGO
    t1_mago_apagado: '&8Apagado'
    t1_mago_inestable: '&7Inestable'
    t1_mago_gastado: '&7Gastado'
    t1_mago_afinado: '&fAfinado'
    t1_mago_canalizador: '&aCanalizador'
    t1_mago_imbuido: '&2Imbuido'

    # PICARO
    t1_picaro_torpe: '&8Torpe'
    t1_picaro_descuidado: '&7Descuidado'
    t1_picaro_gastado: '&7Gastado'
    t1_picaro_agil: '&fAgil'
    t1_picaro_preciso: '&aPreciso'
    t1_picaro_letal: '&2Letal'

    # ARMAS T1 GENERICAS
    arma_t1_mellada: '&8Mellada'
    arma_t1_desgastada: '&7Desgastada'
    arma_t1_tosca: '&7Tosca'
    arma_t1_afilada: '&fAfilada'
    arma_t1_ligera: '&aLigera'
    arma_t1_reforzada: '&2Reforzada'

  qualities:
    bad:
      - t1_tanque_oxidado
      - t1_tanque_abollado
      - t1_tanque_gastado
      - t1_rango_torcido
      - t1_rango_mal_ajustado
      - t1_rango_gastado
      - t1_mago_apagado
      - t1_mago_inestable
      - t1_mago_gastado
      - t1_picaro_torpe
      - t1_picaro_descuidado
      - t1_picaro_gastado
      - arma_t1_mellada
      - arma_t1_desgastada
      - arma_t1_tosca

    normal:
      - t1_normal
      - arma_t1_normal

    good:
      - t1_tanque_pulido
      - t1_tanque_resistente
      - t1_rango_estable
      - t1_rango_certero
      - t1_mago_afinado
      - t1_mago_canalizador
      - t1_picaro_agil
      - t1_picaro_preciso
      - arma_t1_afilada
      - arma_t1_ligera

    very-good:
      - t1_tanque_reforzado
      - t1_rango_preciso
      - t1_mago_imbuido
      - t1_picaro_letal
      - arma_t1_reforzada

    blocked:
      - reliquia
      - simbionte

""";

}
