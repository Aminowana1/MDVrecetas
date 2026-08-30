package com.mdvcraft.mdvrecetas.gui;

import com.mdvcraft.mdvrecetas.MDVRecetasPlugin;
import com.mdvcraft.mdvrecetas.hook.MDVSocialHook;
import com.mdvcraft.mdvrecetas.model.ItemSpec;
import com.mdvcraft.mdvrecetas.model.MdvRecipe;
import com.mdvcraft.mdvrecetas.model.RecipeType;
import com.mdvcraft.mdvrecetas.model.StationType;
import com.mdvcraft.mdvrecetas.recipe.MdvRecipeManager;
import com.mdvcraft.mdvrecetas.service.ItemResolver;
import com.mdvcraft.mdvrecetas.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Visualizador nativo Bedrock/Floodgate para la guía pública de MDVRecetas.
 *
 * Java conserva RecipeGuiManager sin cambios. En Bedrock se usa un sistema híbrido:
 * - Forms para categorías, listas y buscador;
 * - un inventario pequeño de 27 slots para ver la receta físicamente en 3x3;
 * - variantes navegables y acceso a recetas de ingredientes desde esa vista;
 * - buscador por texto o por un objeto elegido del inventario.
 */
public final class BedrockRecipeMenuManager implements Listener {
    private static final String MENU_RESOURCE = "MenusBedrock/recipes.yml";

    private final MDVRecetasPlugin plugin;
    private final MdvRecipeManager recipeManager;
    private final ItemResolver itemResolver;
    private final MDVSocialHook socialHook;

    private final AtomicLong sessionSequence = new AtomicLong();
    private final Map<UUID, Long> activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> consumedSessions = new ConcurrentHashMap<>();

    private YamlConfiguration menu;
    private boolean floodgateAvailable;

    public BedrockRecipeMenuManager(MDVRecetasPlugin plugin, MdvRecipeManager recipeManager,
                                    ItemResolver itemResolver, MDVSocialHook socialHook) {
        this.plugin = plugin;
        this.recipeManager = recipeManager;
        this.itemResolver = itemResolver;
        this.socialHook = socialHook;
        reload();
    }

    public void reload() {
        floodgateAvailable = Bukkit.getPluginManager().isPluginEnabled("floodgate");
        ensureMenuFile();
        this.menu = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), MENU_RESOURCE));
        plugin.getLogger().info("Menu Bedrock de MDVRecetas cargado"
                + (floodgateAvailable ? " (Floodgate detectado)." : " (Floodgate no detectado; Java sin cambios)."));
    }

    public void shutdown() {
        activeSessions.clear();
        consumedSessions.clear();
    }

    /** Devuelve true si el jugador es Bedrock y el menú fue abierto como Form. */
    public boolean openMain(Player player) {
        if (!isBedrock(player)) return false;
        openMainForm(player);
        return true;
    }

    private boolean isBedrock(Player player) {
        if (player == null || !plugin.getConfig().getBoolean("bedrock.enabled", true)) return false;
        if (!floodgateAvailable) floodgateAvailable = Bukkit.getPluginManager().isPluginEnabled("floodgate");
        if (!floodgateAvailable) return false;
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ---------------------------------------------------------------------
    // Menú principal / categorías
    // ---------------------------------------------------------------------

    private void openMainForm(Player player) {
        Map<String, CategoryInfo> categories = categories();
        int total = recipeManager.collapseDisplayGroups(recipeManager.getVisibleRecipes()).size();
        Map<String, String> values = values(
                "total", String.valueOf(total),
                "categories", String.valueOf(categories.size())
        );

        String title = text("main.title", "&8&lGuía de Recetas", values);
        String content = join(lines("main.content", List.of(
                "&7Explora las recetas por categoría o usa el buscador.",
                "&7Recetas visibles: &e{total}"
        ), values));

        List<FormButton> buttons = new ArrayList<>();
        for (CategoryInfo category : categories.values()) {
            int count = recipeManager.collapseDisplayGroups(recipeManager.getVisibleByCategory(category.id())).size();
            if (!menu.getBoolean("main.show-empty-categories", true) && count <= 0) continue;
            Map<String, String> categoryValues = values(values,
                    "category", ColorUtil.stripColor(category.name()),
                    "category_colored", category.name(),
                    "count", String.valueOf(count));
            String label = text("main.category-button", "&e&l{category_colored}\n&r&7Recetas: &f{count}", categoryValues);
            buttons.add(new FormButton(label, () -> openCategory(player, category.id(), 1)));
        }

        buttons.add(new FormButton(text("main.search.text", "&b&lBuscar recetas\n&r&7Por nombre o por objeto en mano.", values),
                () -> openSearchMenu(player)));
        buttons.add(new FormButton(text("main.back.text", "&6&lVolver", values),
                () -> runBackCommand(player)));

        sendSimpleForm(player, title, content, buttons);
        socialHook.play(player, "open");
    }

    private void openCategory(Player player, String category, int requestedPage) {
        String normalizedCategory = normalizeCategory(category);
        List<MdvRecipe> recipes = recipeManager.collapseDisplayGroups(recipeManager.getVisibleByCategory(normalizedCategory));
        recipes = sorted(recipes);

        int pageSize = pageSize("settings.category-page-size", 8);
        int pages = Math.max(1, (int) Math.ceil(recipes.size() / (double) pageSize));
        int page = Math.max(1, Math.min(pages, requestedPage));

        CategoryInfo info = categories().getOrDefault(normalizedCategory,
                new CategoryInfo(normalizedCategory, prettyCategory(normalizedCategory)));
        Map<String, String> values = values(
                "category", ColorUtil.stripColor(info.name()),
                "category_colored", info.name(),
                "page", String.valueOf(page),
                "pages", String.valueOf(pages),
                "total", String.valueOf(recipes.size())
        );
        String title = text("category.title", "&8&l{category_colored}", values);
        List<String> contentLines = lines("category.content", List.of(
                "&7Recetas: &f{total}",
                "&7Página: &f{page}/{pages}"
        ), values);
        if (recipes.isEmpty()) {
            contentLines.add(text("category.empty", "&7No hay recetas visibles en esta categoría.", values));
        }

        List<FormButton> buttons = new ArrayList<>();
        int start = (page - 1) * pageSize;
        int end = Math.min(recipes.size(), start + pageSize);
        for (int i = start; i < end; i++) {
            MdvRecipe recipe = recipes.get(i);
            Map<String, String> recipeValues = recipeValues(recipe);
            String label = text("category.recipe-button", "&e&l{result}\n&r&7{station} &8• &7{type}", recipeValues);
            BackContext back = BackContext.category(normalizedCategory, page);
            buttons.add(new FormButton(label, () -> openRecipe(player, recipe, 0, back)));
        }

        if (page > 1) {
            buttons.add(new FormButton(text("category.previous.text", "&eAnterior", values),
                    () -> openCategory(player, normalizedCategory, page - 1)));
        }
        if (page < pages) {
            buttons.add(new FormButton(text("category.next.text", "&eSiguiente", values),
                    () -> openCategory(player, normalizedCategory, page + 1)));
        }
        buttons.add(new FormButton(text("category.back.text", "&6&lVolver a categorías", values),
                () -> openMainForm(player)));

        sendSimpleForm(player, title, join(contentLines), buttons);
    }

    // ---------------------------------------------------------------------
    // Buscador Bedrock
    // ---------------------------------------------------------------------

    private void openSearchMenu(Player player) {
        List<InventorySearchItem> inventoryItems = searchableInventoryItems(player);
        Map<String, String> values = values("inventory_items", String.valueOf(inventoryItems.size()));
        String title = text("search.menu.title", "&8&lBuscador de Recetas", values);
        String content = join(lines("search.menu.content", List.of(
                "&7Busca escribiendo un nombre o selecciona un objeto de tu inventario.",
                "",
                "&7Objetos detectados: &e{inventory_items}"
        ), values));

        List<FormButton> buttons = new ArrayList<>();
        buttons.add(new FormButton(text("search.menu.text-search.text", "&b&lBuscar por nombre\n&r&7Escribe objeto, ingrediente o receta.", values),
                () -> openTextSearchInput(player)));
        buttons.add(new FormButton(text("search.menu.inventory-search.text", "&a&lElegir objeto del inventario\n&r&7Busca recetas que utilicen uno de tus objetos.", values),
                () -> openInventorySearch(player, 1)));
        buttons.add(new FormButton(text("search.menu.back.text", "&6&lVolver", values),
                () -> openMainForm(player)));
        sendSimpleForm(player, title, content, buttons);
    }

    private void openTextSearchInput(Player player) {
        long session = beginSession(player);
        CustomForm.Builder builder = CustomForm.builder()
                .title(ColorUtil.color(text("search.input.title", "&8&lBuscar Receta", Map.of())))
                .input(
                        ColorUtil.color(text("search.input.label", "&eNombre del objeto, ingrediente o receta", Map.of())),
                        ColorUtil.stripColor(text("search.input.placeholder", "Ej: Espada Lunar, Nimbrel, hierro...", Map.of())),
                        ""
                );
        builder.validResultHandler(response -> {
            String query = response.asInput(0);
            runFormAction(player, session, () -> {
                if (query == null || query.isBlank()) {
                    openSearchMenu(player);
                    return;
                }
                SearchContext context = SearchContext.text(query.trim());
                openSearchResults(player, context, 1);
            });
        });
        sendCustomForm(player, builder);
    }

    private void openInventorySearch(Player player, int requestedPage) {
        List<InventorySearchItem> items = searchableInventoryItems(player);
        int pageSize = pageSize("settings.inventory-page-size", 8);
        int pages = Math.max(1, (int) Math.ceil(items.size() / (double) pageSize));
        int page = Math.max(1, Math.min(pages, requestedPage));

        Map<String, String> values = values(
                "page", String.valueOf(page),
                "pages", String.valueOf(pages),
                "total", String.valueOf(items.size())
        );
        String title = text("search.inventory.title", "&8&lElige un objeto", values);
        List<String> contentLines = lines("search.inventory.content", List.of(
                "&7Selecciona un objeto de tu inventario.",
                "&7Se buscarán recetas que lo utilicen como ingrediente.",
                "",
                "&7Página: &f{page}/{pages}"
        ), values);
        if (items.isEmpty()) {
            contentLines.add(text("search.inventory.empty", "&7No tienes objetos disponibles para buscar.", values));
        }

        List<FormButton> buttons = new ArrayList<>();
        int start = (page - 1) * pageSize;
        int end = Math.min(items.size(), start + pageSize);
        for (int i = start; i < end; i++) {
            InventorySearchItem entry = items.get(i);
            Map<String, String> itemValues = values(values,
                    "item", displayName(entry.item()),
                    "amount", String.valueOf(entry.amount()));
            String label = text("search.inventory.item-button", "&e&l{item}\n&r&7En inventario: &f{amount}", itemValues);
            buttons.add(new FormButton(label,
                    () -> openSearchResults(player, SearchContext.item(entry.item().clone(), displayName(entry.item())), 1)));
        }

        if (page > 1) {
            buttons.add(new FormButton(text("search.inventory.previous.text", "&e&lAnterior", values),
                    () -> openInventorySearch(player, page - 1)));
        }
        if (page < pages) {
            buttons.add(new FormButton(text("search.inventory.next.text", "&e&lSiguiente", values),
                    () -> openInventorySearch(player, page + 1)));
        }
        buttons.add(new FormButton(text("search.inventory.back.text", "&6&lVolver al buscador", values),
                () -> openSearchMenu(player)));

        sendSimpleForm(player, title, join(contentLines), buttons);
    }

    private List<InventorySearchItem> searchableInventoryItems(Player player) {
        if (player == null) return List.of();
        ItemStack[] contents = player.getInventory().getStorageContents();
        int heldSlot = player.getInventory().getHeldItemSlot();
        boolean excludeMainHand = menu == null || menu.getBoolean("search.inventory.exclude-main-hand", true);

        List<InventorySearchItem> result = new ArrayList<>();
        for (int slot = 0; slot < contents.length; slot++) {
            if (excludeMainHand && slot == heldSlot) continue;
            ItemStack stack = contents[slot];
            if (isEmpty(stack)) continue;

            InventorySearchItem matching = null;
            for (InventorySearchItem candidate : result) {
                if (candidate.item().isSimilar(stack)) {
                    matching = candidate;
                    break;
                }
            }
            if (matching != null) {
                int index = result.indexOf(matching);
                result.set(index, new InventorySearchItem(matching.item(), matching.amount() + Math.max(1, stack.getAmount())));
            } else {
                ItemStack representative = stack.clone();
                representative.setAmount(1);
                result.add(new InventorySearchItem(representative, Math.max(1, stack.getAmount())));
            }
        }
        result.sort((a, b) -> ColorUtil.stripColor(displayName(a.item()))
                .compareToIgnoreCase(ColorUtil.stripColor(displayName(b.item()))));
        return result;
    }

    private void openSearchResults(Player player, SearchContext context, int requestedPage) {
        List<MdvRecipe> recipes = searchResults(context);
        int pageSize = pageSize("settings.search-page-size", 8);
        int pages = Math.max(1, (int) Math.ceil(recipes.size() / (double) pageSize));
        int page = Math.max(1, Math.min(pages, requestedPage));

        Map<String, String> values = values(
                "query", context.label(),
                "page", String.valueOf(page),
                "pages", String.valueOf(pages),
                "total", String.valueOf(recipes.size())
        );
        String title = text("search.results.title", "&8&lResultados", values);
        List<String> contentLines = lines("search.results.content", List.of(
                "&7Búsqueda: &e{query}",
                "&7Resultados: &f{total}",
                "&7Página: &f{page}/{pages}"
        ), values);
        if (recipes.isEmpty()) {
            contentLines.add(text("search.results.empty", "&7No encontré recetas visibles con esa búsqueda.", values));
        }

        List<FormButton> buttons = new ArrayList<>();
        int start = (page - 1) * pageSize;
        int end = Math.min(recipes.size(), start + pageSize);
        for (int i = start; i < end; i++) {
            MdvRecipe recipe = recipes.get(i);
            String label = text("search.results.recipe-button", "&e&l{result}\n&r&7{category} &8• &7{station}", recipeValues(recipe));
            BackContext back = BackContext.search(context, page);
            buttons.add(new FormButton(label, () -> openRecipe(player, recipe, 0, back)));
        }
        if (page > 1) {
            buttons.add(new FormButton(text("search.results.previous.text", "&eAnterior", values),
                    () -> openSearchResults(player, context, page - 1)));
        }
        if (page < pages) {
            buttons.add(new FormButton(text("search.results.next.text", "&eSiguiente", values),
                    () -> openSearchResults(player, context, page + 1)));
        }
        buttons.add(new FormButton(text("search.results.new-search.text", "&bNueva búsqueda", values),
                () -> openSearchMenu(player)));
        buttons.add(new FormButton(text("search.results.back.text", "&6Volver", values),
                () -> openMainForm(player)));

        sendSimpleForm(player, title, join(contentLines), buttons);
    }

    private List<MdvRecipe> searchResults(SearchContext context) {
        if (context == null) return List.of();
        if (context.mode() == SearchMode.ITEM) {
            ItemStack item = context.item();
            return item == null ? List.of() : sorted(recipeManager.findVisibleDisplayRecipesUsing(item));
        }
        return textSearch(context.query());
    }

    private List<MdvRecipe> textSearch(String rawQuery) {
        String query = normalizeSearch(rawQuery);
        if (query.isBlank()) return List.of();
        String[] terms = query.split("\\s+");
        List<MdvRecipe> displayRecipes = recipeManager.collapseDisplayGroups(recipeManager.getVisibleRecipes());
        List<MdvRecipe> result = new ArrayList<>();
        for (MdvRecipe representative : displayRecipes) {
            StringBuilder haystack = new StringBuilder();
            for (MdvRecipe variant : recipeManager.getLinkedRecipes(representative, false)) {
                appendSearchText(haystack, variant);
            }
            String normalized = normalizeSearch(haystack.toString());
            boolean matches = true;
            for (String term : terms) {
                if (!normalized.contains(term)) {
                    matches = false;
                    break;
                }
            }
            if (matches) result.add(representative);
        }
        return sorted(result);
    }

    private void appendSearchText(StringBuilder out, MdvRecipe recipe) {
        if (recipe == null) return;
        out.append(' ').append(recipe.getId());
        out.append(' ').append(recipe.getCategory());
        out.append(' ').append(categoryDisplayName(recipe.getCategory()));
        out.append(' ').append(stationName(recipe.getStation()));
        out.append(' ').append(recipeTypeName(recipe.getType()));
        appendSpecSearchText(out, recipe.getResult());
        for (ItemSpec spec : recipe.getShapedIngredients().values()) appendSpecSearchText(out, spec);
        for (ItemSpec spec : recipe.getShapelessIngredients().values()) appendSpecSearchText(out, spec);
        appendSpecSearchText(out, recipe.getCookingIngredient());
    }

    private void appendSpecSearchText(StringBuilder out, ItemSpec spec) {
        if (spec == null) return;
        out.append(' ').append(displayName(spec));
        out.append(' ').append(spec.getKind());
        if (spec.getMaterial() != null) out.append(' ').append(spec.getMaterial().name());
        if (spec.getMmoType() != null) out.append(' ').append(spec.getMmoType());
        if (spec.getMmoId() != null) out.append(' ').append(spec.getMmoId());
    }

    // ---------------------------------------------------------------------
    // Vista de receta
    // ---------------------------------------------------------------------

    private void openRecipe(Player player, MdvRecipe representative, int requestedVariant, BackContext back) {
        if (menu == null || menu.getBoolean("recipe.hybrid-inventory.enabled", true)) {
            openRecipeInventory(player, representative, requestedVariant, back);
            return;
        }
        openRecipeForm(player, representative, requestedVariant, back);
    }

    /**
     * Fallback 100% Forms. Se conserva para poder desactivar la vista híbrida
     * desde YAML sin perder compatibilidad.
     */
    private void openRecipeForm(Player player, MdvRecipe representative, int requestedVariant, BackContext back) {
        if (representative == null) {
            goBack(player, back);
            return;
        }
        List<MdvRecipe> variants = recipeManager.getLinkedRecipes(representative, true);
        if (variants.isEmpty()) variants = List.of(representative);
        variants = sorted(variants);
        int variant = Math.max(0, Math.min(variants.size() - 1, requestedVariant));
        MdvRecipe recipe = variants.get(variant);

        Map<String, String> values = recipeValues(recipe);
        values = values(values,
                "variant", String.valueOf(variant + 1),
                "variants", String.valueOf(variants.size()),
                "forjador_exp", formatDouble(recipe.getForjador().getExp()),
                "cooking_time", String.valueOf(recipe.getCookingTime()),
                "vanilla_exp", formatDouble(recipe.getCookingVanillaExp()));

        String title = text("recipe.title", "&8&l{result}", values);
        List<String> templates = lines("recipe.content", List.of(
                "&e&l● &7&lResultado",
                "&f{result} &7x{result_amount}",
                "",
                "&e&l● &7&lFabricación",
                "&7Estación: &e{station}",
                "&7Categoría: &f{category}",
                "&7Tipo: &f{type}",
                "&7Variante: &f{variant}/{variants}",
                "",
                "{layout}",
                "&e&l● &7&lIngredientes",
                "{ingredients}",
                "{extra}"
        ), values);
        List<String> layoutLines = recipeLayoutLines(recipe, values);
        List<String> ingredientLines = ingredientLines(recipe);
        List<String> extraLines = extraRecipeLines(recipe, values);
        List<String> contentLines = expandRecipeTemplates(templates, values, layoutLines, ingredientLines, extraLines);

        List<FormButton> buttons = new ArrayList<>();
        if (variants.size() > 1) {
            if (variant > 0) {
                buttons.add(new FormButton(text("recipe.previous-variant.text", "&eVariante anterior", values),
                        () -> openRecipe(player, representative, variant - 1, back)));
            }
            if (variant < variants.size() - 1) {
                buttons.add(new FormButton(text("recipe.next-variant.text", "&eSiguiente variante", values),
                        () -> openRecipe(player, representative, variant + 1, back)));
            }
        }

        if (menu.getBoolean("recipe.linked-ingredients.enabled", true)) {
            int maxLinks = Math.max(0, Math.min(9, menu.getInt("recipe.linked-ingredients.max-buttons", 6)));
            int added = 0;
            Set<String> seen = new LinkedHashSet<>();
            for (ItemSpec ingredient : uniqueIngredients(recipe)) {
                if (added >= maxLinks) break;
                MdvRecipe target = recipeManager.findVisibleRecipeProducing(ingredient).orElse(null);
                if (target == null) continue;
                MdvRecipe targetRepresentative = recipeManager.displayRepresentative(target);
                if (targetRepresentative == null) continue;
                String targetKey = targetRepresentative.getId().toLowerCase(Locale.ROOT);
                if (!seen.add(targetKey)) continue;
                // Evita un botón que apunte a la misma receta/grupo actual.
                if (sameDisplayGroup(representative, targetRepresentative)) continue;

                Map<String, String> linkValues = values(
                        "ingredient", displayName(ingredient),
                        "result", displayName(targetRepresentative.getResult()));
                String label = text("recipe.linked-ingredients.button", "&bVer receta de {ingredient}", linkValues);
                BackContext parentBack = BackContext.detail(representative, variant, back);
                buttons.add(new FormButton(label, () -> openRecipe(player, targetRepresentative, 0, parentBack)));
                added++;
            }
        }

        buttons.add(new FormButton(text("recipe.back.text", "&6&lVolver", values), () -> goBack(player, back)));
        sendSimpleForm(player, title, join(contentLines), buttons);
    }


    // ---------------------------------------------------------------------
    // Vista híbrida Bedrock: Form -> inventario visual 3x3
    // ---------------------------------------------------------------------

    private void openRecipeInventory(Player player, MdvRecipe representative, int requestedVariant, BackContext back) {
        if (player == null || !player.isOnline()) return;
        if (representative == null) {
            goBack(player, back);
            return;
        }

        List<MdvRecipe> variants = recipeManager.getLinkedRecipes(representative, true);
        if (variants.isEmpty()) variants = List.of(representative);
        variants = sorted(variants);
        int variant = Math.max(0, Math.min(variants.size() - 1, requestedVariant));
        MdvRecipe recipe = variants.get(variant);

        Map<String, String> values = recipeValues(recipe);
        values = values(values,
                "variant", String.valueOf(variant + 1),
                "variants", String.valueOf(variants.size()),
                "forjador_exp", formatDouble(recipe.getForjador().getExp()),
                "cooking_time", String.valueOf(recipe.getCookingTime()),
                "vanilla_exp", formatDouble(recipe.getCookingVanillaExp()));

        int size = hybridInventorySize();
        String title = text("recipe.hybrid-inventory.title", "&8&l{result}", values);
        BedrockRecipeGridHolder holder = new BedrockRecipeGridHolder(
                representative, variant, back, size);
        Inventory inventory = Bukkit.createInventory(holder, size, ColorUtil.color(title));
        holder.setInventory(inventory);
        renderRecipeInventory(holder, recipe, variants, values);

        activeSessions.remove(player.getUniqueId());
        consumedSessions.remove(player.getUniqueId());

        player.openInventory(inventory);
        socialHook.play(player, "open");
    }

    private void renderRecipeInventory(BedrockRecipeGridHolder holder, MdvRecipe recipe,
                                       List<MdvRecipe> variants, Map<String, String> values) {
        Inventory inventory = holder.getInventory();
        if (inventory == null) return;

        holder.clearIngredientSlots();
        fillHybridBackground(inventory, values);

        List<Integer> grid = hybridGridSlots(inventory.getSize());

        if (recipe.getType() == RecipeType.SHAPED) {
            for (int i = 0; i < Math.min(9, grid.size()); i++) {
                int row = i / 3;
                int col = i % 3;
                String line = row < recipe.getShape().size() ? recipe.getShape().get(row) : "";
                char symbol = col < line.length() ? line.charAt(col) : ' ';
                ItemSpec spec = symbol == ' ' ? null : recipe.getShapedIngredients().get(symbol);
                setHybridIngredient(inventory, holder, grid.get(i), spec);
            }
        } else if (recipe.getType() == RecipeType.SHAPELESS) {
            int index = 0;
            for (ItemSpec spec : recipe.getShapelessIngredients().values()) {
                if (index >= grid.size()) break;
                setHybridIngredient(inventory, holder, grid.get(index++), spec);
            }
            while (index < grid.size()) {
                setHybridIngredient(inventory, holder, grid.get(index++), null);
            }
        } else {
            for (int slot : grid) setHybridIngredient(inventory, holder, slot, null);
            int cookingInput = hybridSlot("recipe.hybrid-inventory.cooking.ingredient-slot", 10, inventory.getSize());
            setHybridIngredient(inventory, holder, cookingInput, recipe.getCookingIngredient());
        }

        int stationSlot = hybridSlot("recipe.hybrid-inventory.station.slot", 13, inventory.getSize());
        inventory.setItem(stationSlot, hybridStationItem(recipe, values));

        int arrowSlot = hybridSlot("recipe.hybrid-inventory.arrow.slot", 14, inventory.getSize());
        inventory.setItem(arrowSlot, hybridControlItem(
                menuMaterial("recipe.hybrid-inventory.arrow.material", Material.SPECTRAL_ARROW),
                text("recipe.hybrid-inventory.arrow.name", "&e&l→ Resultado", values),
                lines("recipe.hybrid-inventory.arrow.lore",
                        List.of("&7La receta produce el objeto de la derecha."), values)));

        int resultSlot = hybridSlot("recipe.hybrid-inventory.result.slot", 15, inventory.getSize());
        inventory.setItem(resultSlot, hybridResultItem(recipe, values));

        if (variants.size() > 1) {
            if (holder.getVariant() > 0) {
                int previousSlot = hybridSlot("recipe.hybrid-inventory.previous-variant.slot", 22, inventory.getSize());
                inventory.setItem(previousSlot, hybridControlItem(
                        menuMaterial("recipe.hybrid-inventory.previous-variant.material", Material.ARROW),
                        text("recipe.hybrid-inventory.previous-variant.name", "&e&lVariante anterior", values),
                        lines("recipe.hybrid-inventory.previous-variant.lore",
                                List.of("&7Variante &e{variant}&7/&e{variants}"), values)));
            }
            if (holder.getVariant() < variants.size() - 1) {
                int nextSlot = hybridSlot("recipe.hybrid-inventory.next-variant.slot", 24, inventory.getSize());
                inventory.setItem(nextSlot, hybridControlItem(
                        menuMaterial("recipe.hybrid-inventory.next-variant.material", Material.ARROW),
                        text("recipe.hybrid-inventory.next-variant.name", "&e&lSiguiente variante", values),
                        lines("recipe.hybrid-inventory.next-variant.lore",
                                List.of("&7Variante &e{variant}&7/&e{variants}"), values)));
            }
        }

        int backSlot = hybridSlot("recipe.hybrid-inventory.back.slot", 26, inventory.getSize());
        inventory.setItem(backSlot, hybridControlItem(
                menuMaterial("recipe.hybrid-inventory.back.material", Material.BARRIER),
                text("recipe.hybrid-inventory.back.name", "&6&lVolver", values),
                lines("recipe.hybrid-inventory.back.lore",
                        List.of("&7Regresa al menú anterior."), values)));
    }

    private void fillHybridBackground(Inventory inventory, Map<String, String> values) {
        Material material = menuMaterial("recipe.hybrid-inventory.background.material", Material.BLACK_STAINED_GLASS_PANE);
        String name = text("recipe.hybrid-inventory.background.name", " ", values);
        ItemStack filler = hybridControlItem(material, name, List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler.clone());
        }

        for (int slot : hybridGridSlots(inventory.getSize())) {
            setHybridEmpty(inventory, slot);
        }
    }

    private void setHybridIngredient(Inventory inventory, BedrockRecipeGridHolder holder, int slot, ItemSpec spec) {
        if (slot < 0 || slot >= inventory.getSize()) return;
        if (spec == null) {
            setHybridEmpty(inventory, slot);
            holder.getIngredientSlots().remove(slot);
            return;
        }

        ItemStack item;
        try {
            item = itemResolver.buildItem(spec);
        } catch (Throwable throwable) {
            item = null;
        }
        if (isEmpty(item)) {
            item = hybridControlItem(Material.BARRIER,
                    text("recipe.hybrid-inventory.invalid-ingredient.name", "&cIngrediente inválido", Map.of()),
                    lines("recipe.hybrid-inventory.invalid-ingredient.lore",
                            List.of("&7Revisa el YAML de esta receta."), Map.of()));
        } else {
            item = item.clone();
            item.setAmount(Math.max(1, spec.getAmount()));

            MdvRecipe linked = null;
            if (menu == null || menu.getBoolean("recipe.hybrid-inventory.ingredient.linked-click", true)) {
                linked = recipeManager.findVisibleRecipeProducing(spec).orElse(null);
            }
            Map<String, String> values = values(
                    "ingredient", displayName(spec),
                    "amount", String.valueOf(Math.max(1, spec.getAmount())));
            List<String> append = new ArrayList<>(lines(
                    "recipe.hybrid-inventory.ingredient.lore",
                    List.of("", "&8Ingrediente &7• &e{amount}x"), values));
            if (linked != null) {
                append.addAll(lines("recipe.hybrid-inventory.ingredient.linked-lore",
                        List.of("&bToca para ver cómo se fabrica."), values));
            }
            appendLore(item, append);
        }

        inventory.setItem(slot, item);
        holder.getIngredientSlots().put(slot, spec);
    }

    private void setHybridEmpty(Inventory inventory, int slot) {
        if (slot < 0 || slot >= inventory.getSize()) return;
        Material empty = menuMaterial("recipe.hybrid-inventory.grid.empty-material", Material.AIR);
        if (empty.isAir()) {
            inventory.setItem(slot, null);
            return;
        }
        inventory.setItem(slot, hybridControlItem(empty,
                text("recipe.hybrid-inventory.grid.empty-name", " ", Map.of()), List.of()));
    }

    private ItemStack hybridResultItem(MdvRecipe recipe, Map<String, String> values) {
        ItemStack item;
        try {
            item = itemResolver.buildItem(recipe.getResult());
        } catch (Throwable throwable) {
            item = null;
        }
        if (isEmpty(item)) {
            return hybridControlItem(Material.BARRIER, "&cResultado inválido",
                    List.of("&7Revisa el YAML de esta receta."));
        }
        item = item.clone();
        appendLore(item, lines("recipe.hybrid-inventory.result.lore",
                List.of(
                        "",
                        "&a&lResultado",
                        "&7Cantidad: &e{result_amount}",
                        "&7Categoría: &f{category}",
                        "&7Tipo: &f{type}"
                ), values));
        return item;
    }

    private ItemStack hybridStationItem(MdvRecipe recipe, Map<String, String> values) {
        List<String> lore = new ArrayList<>(lines("recipe.hybrid-inventory.station.lore",
                List.of(
                        "&7Estación: &e{station}",
                        "&7Tipo: &f{type}",
                        "&7Variante: &f{variant}/{variants}"
                ), values));
        if (recipe.getForjador().getExp() > 0) {
            lore.add(text("recipe.hybrid-inventory.station.forjador-line",
                    "&7Forjador: &e+{forjador_exp} EXP", values));
        }
        if (recipe.getType() == RecipeType.COOKING) {
            lore.add(text("recipe.hybrid-inventory.station.cooking-time-line",
                    "&7Tiempo: &e{cooking_time} ticks", values));
            lore.add(text("recipe.hybrid-inventory.station.vanilla-exp-line",
                    "&7EXP vanilla: &e{vanilla_exp}", values));
        }
        return hybridControlItem(stationMaterial(recipe.getStation()),
                text("recipe.hybrid-inventory.station.name", "&6&l{station}", values), lore);
    }

    private ItemStack hybridControlItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material == null || material.isAir() ? Material.STONE : material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.color(name == null ? " " : name));
            List<String> colored = new ArrayList<>();
            if (lore != null) {
                for (String line : lore) colored.add(ColorUtil.color(line));
            }
            meta.setLore(colored);
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    private void appendLore(ItemStack item, List<String> extraLore) {
        if (item == null || extraLore == null || extraLore.isEmpty()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        List<String> lore = meta.hasLore() && meta.getLore() != null
                ? new ArrayList<>(meta.getLore())
                : new ArrayList<>();
        for (String line : extraLore) lore.add(ColorUtil.color(line));
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private int hybridInventorySize() {
        int configured = menu == null ? 27 : menu.getInt("recipe.hybrid-inventory.size", 27);
        if (configured < 9) configured = 9;
        if (configured > 54) configured = 54;
        configured = ((configured + 8) / 9) * 9;
        return configured;
    }

    private List<Integer> hybridGridSlots(int size) {
        List<Integer> configured = menu == null
                ? List.of()
                : menu.getIntegerList("recipe.hybrid-inventory.grid.slots");
        List<Integer> source = configured.size() >= 9
                ? configured
                : List.of(0, 1, 2, 9, 10, 11, 18, 19, 20);
        List<Integer> result = new ArrayList<>(9);
        for (int slot : source) {
            if (slot >= 0 && slot < size && !result.contains(slot)) result.add(slot);
            if (result.size() >= 9) break;
        }
        return result;
    }

    private int hybridSlot(String path, int fallback, int size) {
        int configured = menu == null ? fallback : menu.getInt(path, fallback);
        if (configured < 0 || configured >= size) return Math.max(0, Math.min(size - 1, fallback));
        return configured;
    }

    private Material menuMaterial(String path, Material fallback) {
        String raw = menu == null ? null : menu.getString(path);
        Material material = raw == null ? null : Material.matchMaterial(raw);
        return material == null ? fallback : material;
    }

    private Material stationMaterial(StationType station) {
        return switch (station) {
            case CRAFTING_TABLE -> Material.CRAFTING_TABLE;
            case FURNACE -> Material.FURNACE;
            case BLAST_FURNACE -> Material.BLAST_FURNACE;
            case SMOKER -> Material.SMOKER;
            case CAMPFIRE -> Material.CAMPFIRE;
        };
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onHybridInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof BedrockRecipeGridHolder holder)) return;

        event.setCancelled(true);

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) return;

        int backSlot = hybridSlot("recipe.hybrid-inventory.back.slot", 26, holder.getInventory().getSize());
        if (rawSlot == backSlot) {
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> goBack(player, holder.getBack()));
            socialHook.play(player, "back");
            return;
        }

        List<MdvRecipe> variants = recipeManager.getLinkedRecipes(holder.getRepresentative(), true);
        if (variants.isEmpty()) variants = List.of(holder.getRepresentative());
        variants = sorted(variants);

        int previousSlot = hybridSlot("recipe.hybrid-inventory.previous-variant.slot", 22, holder.getInventory().getSize());
        if (rawSlot == previousSlot && holder.getVariant() > 0) {
            player.closeInventory();
            int targetVariant = holder.getVariant() - 1;
            Bukkit.getScheduler().runTask(plugin,
                    () -> openRecipe(player, holder.getRepresentative(), targetVariant, holder.getBack()));
            socialHook.play(player, "page");
            return;
        }

        int nextSlot = hybridSlot("recipe.hybrid-inventory.next-variant.slot", 24, holder.getInventory().getSize());
        if (rawSlot == nextSlot && holder.getVariant() < variants.size() - 1) {
            player.closeInventory();
            int targetVariant = holder.getVariant() + 1;
            Bukkit.getScheduler().runTask(plugin,
                    () -> openRecipe(player, holder.getRepresentative(), targetVariant, holder.getBack()));
            socialHook.play(player, "page");
            return;
        }

        ItemSpec ingredient = holder.getIngredientSlots().get(rawSlot);
        if (ingredient == null) return;
        if (menu != null && !menu.getBoolean("recipe.hybrid-inventory.ingredient.linked-click", true)) return;

        MdvRecipe target = recipeManager.findVisibleRecipeProducing(ingredient).orElse(null);
        if (target == null) {
            socialHook.play(player, "invalid");
            return;
        }
        MdvRecipe targetRepresentative = recipeManager.displayRepresentative(target);
        if (targetRepresentative == null || sameDisplayGroup(holder.getRepresentative(), targetRepresentative)) {
            socialHook.play(player, "invalid");
            return;
        }

        BackContext parentBack = BackContext.detail(
                holder.getRepresentative(), holder.getVariant(), holder.getBack());
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin,
                () -> openRecipe(player, targetRepresentative, 0, parentBack));
        socialHook.play(player, "open");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onHybridInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BedrockRecipeGridHolder) {
            event.setCancelled(true);
        }
    }

    private List<String> recipeLayoutLines(MdvRecipe recipe, Map<String, String> values) {
        List<String> result = new ArrayList<>();
        if (recipe == null || (menu != null && !menu.getBoolean("recipe.layout.enabled", true))) return result;

        if (recipe.getType() == RecipeType.SHAPED) {
            result.add(text("recipe.layout.shaped-title", "&e&l● &7&lForma en la mesa 3x3", values));
            for (int row = 0; row < 3; row++) {
                String shapeRow = row < recipe.getShape().size() ? recipe.getShape().get(row) : "";
                String c1 = shapeCell(shapeRow, 0);
                String c2 = shapeCell(shapeRow, 1);
                String c3 = shapeCell(shapeRow, 2);
                Map<String, String> rowValues = values(values, "c1", c1, "c2", c2, "c3", c3);
                result.add(text("recipe.layout.shaped-row", "&8[&e{c1}&8] [&e{c2}&8] [&e{c3}&8]", rowValues));
            }

            LinkedHashSet<Character> symbols = new LinkedHashSet<>();
            for (String row : recipe.getShape()) {
                if (row == null) continue;
                for (int i = 0; i < Math.min(3, row.length()); i++) {
                    char symbol = row.charAt(i);
                    if (symbol != ' ') symbols.add(symbol);
                }
            }
            if (!symbols.isEmpty()) {
                result.add(text("recipe.layout.legend-title", "&8Leyenda:", values));
                for (char symbol : symbols) {
                    ItemSpec spec = recipe.getShapedIngredients().get(symbol);
                    if (spec == null) continue;
                    Map<String, String> legendValues = values(values,
                            "symbol", String.valueOf(symbol),
                            "ingredient", displayName(spec),
                            "amount", String.valueOf(Math.max(1, spec.getAmount())));
                    result.add(text("recipe.layout.legend-line", "&e{symbol} &8= &f{amount}x &7{ingredient}", legendValues));
                }
            }
            result.add("");
            return result;
        }

        if (recipe.getType() == RecipeType.SHAPELESS) {
            result.add(text("recipe.layout.shapeless-title", "&e&l● &7&lColocación", values));
            result.add(text("recipe.layout.shapeless-line", "&7Sin forma: coloca los ingredientes en cualquier orden.", values));
            result.add("");
            return result;
        }

        ItemSpec ingredient = recipe.getCookingIngredient();
        Map<String, String> cookingValues = values(values,
                "ingredient", displayName(ingredient),
                "ingredient_amount", String.valueOf(ingredient == null ? 1 : Math.max(1, ingredient.getAmount())),
                "result", displayName(recipe.getResult()),
                "result_amount", String.valueOf(recipe.getResult() == null ? 1 : Math.max(1, recipe.getResult().getAmount())));
        result.add(text("recipe.layout.cooking-title", "&e&l● &7&lProceso", cookingValues));
        result.add(text("recipe.layout.cooking-line", "&f{ingredient_amount}x {ingredient} &8→ &e{result_amount}x {result}", cookingValues));
        result.add("");
        return result;
    }

    private String shapeCell(String row, int column) {
        if (row == null || column < 0 || column >= row.length()) return " ";
        char symbol = row.charAt(column);
        return symbol == ' ' ? " " : String.valueOf(symbol);
    }

    private List<String> ingredientLines(MdvRecipe recipe) {
        LinkedHashMap<String, IngredientLine> aggregated = new LinkedHashMap<>();
        if (recipe.getType() == RecipeType.SHAPED) {
            for (String row : recipe.getShape()) {
                if (row == null) continue;
                for (int i = 0; i < row.length(); i++) {
                    ItemSpec spec = recipe.getShapedIngredients().get(row.charAt(i));
                    addIngredient(aggregated, spec, spec == null ? 0 : Math.max(1, spec.getAmount()));
                }
            }
        } else if (recipe.getType() == RecipeType.SHAPELESS) {
            for (ItemSpec spec : recipe.getShapelessIngredients().values()) {
                addIngredient(aggregated, spec, spec == null ? 0 : Math.max(1, spec.getAmount()));
            }
        } else {
            ItemSpec spec = recipe.getCookingIngredient();
            addIngredient(aggregated, spec, spec == null ? 0 : Math.max(1, spec.getAmount()));
        }

        if (aggregated.isEmpty()) {
            return List.of(text("recipe.no-ingredients", "&8Sin ingredientes visibles.", Map.of()));
        }
        List<String> lines = new ArrayList<>();
        for (IngredientLine line : aggregated.values()) {
            Map<String, String> values = values(
                    "amount", String.valueOf(line.amount()),
                    "ingredient", line.name());
            lines.add(text("recipe.ingredient-line", "&7• &f{amount}x &e{ingredient}", values));
        }
        return lines;
    }

    private void addIngredient(Map<String, IngredientLine> aggregated, ItemSpec spec, int amount) {
        if (spec == null || amount <= 0) return;
        String key = ingredientKey(spec);
        IngredientLine existing = aggregated.get(key);
        if (existing == null) {
            aggregated.put(key, new IngredientLine(displayName(spec), amount));
        } else {
            aggregated.put(key, new IngredientLine(existing.name(), existing.amount() + amount));
        }
    }

    private List<ItemSpec> uniqueIngredients(MdvRecipe recipe) {
        LinkedHashMap<String, ItemSpec> result = new LinkedHashMap<>();
        for (ItemSpec spec : recipe.getShapedIngredients().values()) if (spec != null) result.putIfAbsent(ingredientKey(spec), spec);
        for (ItemSpec spec : recipe.getShapelessIngredients().values()) if (spec != null) result.putIfAbsent(ingredientKey(spec), spec);
        if (recipe.getCookingIngredient() != null) result.putIfAbsent(ingredientKey(recipe.getCookingIngredient()), recipe.getCookingIngredient());
        return new ArrayList<>(result.values());
    }

    private List<String> extraRecipeLines(MdvRecipe recipe, Map<String, String> values) {
        List<String> result = new ArrayList<>();
        if (recipe.getForjador().getExp() > 0) {
            result.add("");
            result.add(text("recipe.forjador-line", "&7EXP de Forjador: &e+{forjador_exp}", values));
        }
        if (recipe.getType() == RecipeType.COOKING) {
            result.add("");
            result.add(text("recipe.cooking-time-line", "&7Tiempo: &e{cooking_time} ticks", values));
            result.add(text("recipe.vanilla-exp-line", "&7EXP vanilla: &e{vanilla_exp}", values));
        }
        return result;
    }

    private List<String> expandRecipeTemplates(List<String> templates, Map<String, String> values,
                                               List<String> layout, List<String> ingredients, List<String> extra) {
        List<String> result = new ArrayList<>();
        for (String raw : templates) {
            String marker = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
            switch (marker) {
                case "{layout}" -> result.addAll(layout);
                case "{ingredients}" -> result.addAll(ingredients);
                case "{extra}" -> result.addAll(extra);
                default -> result.add(render(raw, values));
            }
        }
        return result;
    }

    private void goBack(Player player, BackContext back) {
        if (back == null || back.type() == BackType.MAIN) {
            openMainForm(player);
            return;
        }
        switch (back.type()) {
            case CATEGORY -> openCategory(player, back.category(), back.page());
            case SEARCH -> openSearchResults(player, back.search(), back.page());
            case DETAIL -> openRecipe(player, back.recipe(), back.variant(), back.parent());
            case MAIN -> openMainForm(player);
        }
        socialHook.play(player, "back");
    }

    // ---------------------------------------------------------------------
    // Forms / sesiones
    // ---------------------------------------------------------------------

    private void sendSimpleForm(Player player, String title, String content, List<FormButton> buttons) {
        if (!player.isOnline()) return;
        long session = beginSession(player);
        List<FormButton> safeButtons = buttons == null ? List.of() : List.copyOf(buttons);
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(ColorUtil.color(title))
                .content(ColorUtil.color(content));
        for (FormButton button : safeButtons) builder.button(ColorUtil.color(button.text()));
        if (safeButtons.isEmpty()) builder.button(ColorUtil.color("&6Volver"));
        builder.closedResultHandler(() -> { });
        builder.validResultHandler(response -> {
            int index = response.clickedButtonId();
            runFormAction(player, session, () -> {
                if (index < 0 || index >= safeButtons.size()) return;
                safeButtons.get(index).action().run();
            });
        });
        sendSimpleForm(player, builder);
    }

    private boolean sendSimpleForm(Player player, SimpleForm.Builder builder) {
        if (!isBedrock(player)) return false;
        try {
            FloodgatePlayer floodgatePlayer = FloodgateApi.getInstance().getPlayer(player.getUniqueId());
            if (floodgatePlayer == null) return false;
            return floodgatePlayer.sendForm(builder.build());
        } catch (Throwable ex) {
            plugin.getLogger().warning("No se pudo enviar SimpleForm de MDVRecetas a " + player.getName() + ": " + ex.getMessage());
            return false;
        }
    }

    private boolean sendCustomForm(Player player, CustomForm.Builder builder) {
        if (!isBedrock(player)) return false;
        try {
            FloodgatePlayer floodgatePlayer = FloodgateApi.getInstance().getPlayer(player.getUniqueId());
            if (floodgatePlayer == null) return false;
            return floodgatePlayer.sendForm(builder.build());
        } catch (Throwable ex) {
            plugin.getLogger().warning("No se pudo enviar CustomForm de MDVRecetas a " + player.getName() + ": " + ex.getMessage());
            return false;
        }
    }

    private long beginSession(Player player) {
        long token = sessionSequence.incrementAndGet();
        activeSessions.put(player.getUniqueId(), token);
        consumedSessions.remove(player.getUniqueId());
        return token;
    }

    private void runFormAction(Player player, long expectedSession, Runnable action) {
        if (player == null || action == null || !player.isOnline()) return;
        UUID uuid = player.getUniqueId();
        synchronized (this) {
            Long active = activeSessions.get(uuid);
            if (active == null || active.longValue() != expectedSession) return;
            Long consumed = consumedSessions.get(uuid);
            if (consumed != null && consumed.longValue() == expectedSession) return;
            consumedSessions.put(uuid, expectedSession);
        }
        long delay = Math.max(0L, Math.min(5L, plugin.getConfig().getLong("bedrock.navigation-delay-ticks", 1L)));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            Long active = activeSessions.get(uuid);
            if (active == null || active.longValue() != expectedSession) return;
            action.run();
        }, delay);
    }

    // ---------------------------------------------------------------------
    // YAML / formato
    // ---------------------------------------------------------------------

    private void ensureMenuFile() {
        File file = new File(plugin.getDataFolder(), MENU_RESOURCE);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try {
            if (!file.exists()) {
                plugin.saveResource(MENU_RESOURCE, false);
                return;
            }
            try (InputStream input = plugin.getResource(MENU_RESOURCE)) {
                if (input == null) return;
                YamlConfiguration current = YamlConfiguration.loadConfiguration(file);
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(input, StandardCharsets.UTF_8));
                migrateLegacyBedrockMenu(current);
                current.setDefaults(defaults);
                current.options().copyDefaults(true);
                current.save(file);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("No se pudo preparar " + MENU_RESOURCE + ": " + ex.getMessage());
        }
    }

    /**
     * Migra únicamente valores que coinciden exactamente con los defaults de 0.6.17.
     * Así las instalaciones existentes reciben las dos mejoras de 0.6.18 sin
     * sobreescribir textos que el administrador haya personalizado.
     */
    private void migrateLegacyBedrockMenu(YamlConfiguration current) {
        if (current == null) return;

        String oldMainSearch = "&b&lBuscar recetas\n&r&7Por nombre o por objeto en mano.";
        if (oldMainSearch.equals(current.getString("main.search.text"))) {
            current.set("main.search.text", "&b&lBuscar recetas\n&r&7Por nombre o por un objeto de tu inventario.");
        }

        List<String> oldSearchContent = List.of(
                "&7Busca recetas por nombre o usa",
                "&7exactamente el objeto que sostienes.",
                "",
                "&7Objeto en mano: &e{hand}"
        );
        if (oldSearchContent.equals(current.getStringList("search.menu.content"))) {
            current.set("search.menu.content", List.of(
                    "&7Busca escribiendo un nombre o selecciona",
                    "&7un objeto que ya tengas en tu inventario.",
                    "",
                    "&7Objetos detectados: &e{inventory_items}"
            ));
        }

        List<String> oldRecipeContent = List.of(
                "&e&l● &7&lResultado",
                "&f{result} &7x{result_amount}",
                "",
                "&e&l● &7&lFabricación",
                "&7Estación: &e{station}",
                "&7Categoría: &f{category}",
                "&7Tipo: &f{type}",
                "&7Variante: &f{variant}/{variants}",
                "",
                "&e&l● &7&lIngredientes",
                "{ingredients}",
                "{extra}"
        );
        if (oldRecipeContent.equals(current.getStringList("recipe.content"))) {
            current.set("recipe.content", List.of(
                    "&e&l● &7&lResultado",
                    "&f{result} &7x{result_amount}",
                    "",
                    "&e&l● &7&lFabricación",
                    "&7Estación: &e{station}",
                    "&7Categoría: &f{category}",
                    "&7Tipo: &f{type}",
                    "&7Variante: &f{variant}/{variants}",
                    "",
                    "{layout}",
                    "&e&l● &7&lIngredientes",
                    "{ingredients}",
                    "{extra}"
            ));
        }
    }

    private String text(String path, String fallback, Map<String, String> values) {
        Object raw = menu == null ? null : menu.get(path);
        String value;
        if (raw instanceof ConfigurationSection section) {
            value = section.getString("text", fallback);
        } else {
            value = menu == null ? fallback : menu.getString(path, fallback);
        }
        return render(value, values);
    }

    private List<String> lines(String path, List<String> fallback, Map<String, String> values) {
        List<String> configured = menu == null ? List.of() : menu.getStringList(path);
        List<String> source = configured.isEmpty() ? fallback : configured;
        List<String> result = new ArrayList<>();
        for (String line : source) result.add(render(line, values));
        return result;
    }

    private String render(String raw, Map<String, String> values) {
        String result = raw == null ? "" : raw;
        if (values != null) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
            }
        }
        return result;
    }

    private Map<String, String> recipeValues(MdvRecipe recipe) {
        ItemSpec resultSpec = recipe.getResult();
        return values(
                "id", recipe.getId(),
                "result", displayName(resultSpec),
                "result_amount", String.valueOf(resultSpec == null ? 1 : Math.max(1, resultSpec.getAmount())),
                "category", ColorUtil.stripColor(categoryDisplayName(recipe.getCategory())),
                "category_colored", categoryDisplayName(recipe.getCategory()),
                "station", stationName(recipe.getStation()),
                "type", recipeTypeName(recipe.getType())
        );
    }

    private static Map<String, String> values(String... pairs) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        if (pairs == null) return map;
        for (int i = 0; i + 1 < pairs.length; i += 2) map.put(pairs[i], pairs[i + 1]);
        return map;
    }

    private static Map<String, String> values(Map<String, String> base, String... pairs) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        if (base != null) map.putAll(base);
        if (pairs != null) {
            for (int i = 0; i + 1 < pairs.length; i += 2) map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private String join(List<String> lines) {
        return String.join("\n", lines == null ? List.of() : lines);
    }

    private int pageSize(String path, int fallback) {
        int configured = menu == null ? fallback : menu.getInt(path, fallback);
        return Math.max(3, Math.min(15, configured));
    }

    private Map<String, CategoryInfo> categories() {
        LinkedHashMap<String, CategoryInfo> result = new LinkedHashMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("gui.categories");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection category = section.getConfigurationSection(id);
                if (category == null) continue;
                String normalized = normalizeCategory(id);
                result.put(normalized, new CategoryInfo(normalized,
                        category.getString("name", "&f" + prettyCategory(normalized))));
            }
        }
        if (!result.isEmpty()) return result;
        for (MdvRecipe recipe : recipeManager.getVisibleRecipes()) {
            String id = normalizeCategory(recipe.getCategory());
            result.putIfAbsent(id, new CategoryInfo(id, "&f" + prettyCategory(id)));
        }
        return result;
    }

    private String categoryDisplayName(String category) {
        String normalized = normalizeCategory(category);
        CategoryInfo info = categories().get(normalized);
        return info == null ? prettyCategory(normalized) : info.name();
    }

    private String normalizeCategory(String category) {
        return category == null || category.isBlank() ? "GENERAL" : category.toUpperCase(Locale.ROOT);
    }

    private String prettyCategory(String category) {
        if (category == null || category.isBlank()) return "General";
        return prettyEnum(category);
    }

    private String stationName(StationType station) {
        return switch (station) {
            case CRAFTING_TABLE -> "Mesa de crafteo";
            case FURNACE -> "Horno";
            case BLAST_FURNACE -> "Alto horno";
            case SMOKER -> "Ahumador";
            case CAMPFIRE -> "Hoguera";
        };
    }

    private String recipeTypeName(RecipeType type) {
        return switch (type) {
            case SHAPED -> "Con forma";
            case SHAPELESS -> "Sin forma";
            case COOKING -> "Cocción";
        };
    }

    private String displayName(ItemSpec spec) {
        if (spec == null) return "Desconocido";
        try {
            ItemStack item = itemResolver.buildItem(spec);
            if (!isEmpty(item)) return displayName(item);
        } catch (Throwable ignored) {
        }
        if (spec.getMmoId() != null && !spec.getMmoId().isBlank()) return prettyEnum(spec.getMmoId());
        if (spec.getMaterial() != null) return prettyEnum(spec.getMaterial().name());
        return prettyEnum(spec.getKind().name());
    }

    private String displayName(ItemStack item) {
        if (isEmpty(item)) return "Nada";
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName() && meta.getDisplayName() != null && !meta.getDisplayName().isBlank()) {
            return meta.getDisplayName();
        }
        return prettyEnum(item.getType().name());
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getType().isAir();
    }

    private String prettyEnum(String value) {
        if (value == null || value.isBlank()) return "Desconocido";
        String lower = value.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
        StringBuilder builder = new StringBuilder();
        for (String part : lower.split("\\s+")) {
            if (part.isBlank()) continue;
            if (!builder.isEmpty()) builder.append(' ');
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private String normalizeSearch(String input) {
        String stripped = ColorUtil.stripColor(input == null ? "" : input);
        String normalized = Normalizer.normalize(stripped, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized;
    }

    private String ingredientKey(ItemSpec spec) {
        if (spec == null) return "null";
        return spec.getKind() + "|" + spec.getMaterial() + "|" + spec.getMmoType() + "|" + spec.getMmoId()
                + "|" + spec.getMatchMode() + "|" + spec.getData();
    }

    private boolean sameDisplayGroup(MdvRecipe a, MdvRecipe b) {
        if (a == null || b == null) return false;
        if (a.getId().equalsIgnoreCase(b.getId())) return true;
        return a.hasVisualGroup() && b.hasVisualGroup()
                && a.getVisualGroup().equalsIgnoreCase(b.getVisualGroup());
    }

    private List<MdvRecipe> sorted(List<MdvRecipe> input) {
        List<MdvRecipe> result = new ArrayList<>(input == null ? List.of() : input);
        result.sort(recipeManager.displayComparator().thenComparing(MdvRecipe::getId, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private String formatDouble(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.000001D) return String.valueOf((long) Math.rint(value));
        return String.format(Locale.US, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private void runBackCommand(Player player) {
        String command = menu == null ? null : menu.getString("main.back-command");
        if (command == null || command.isBlank()) {
            command = plugin.getConfig().getString("gui.main-back-command", "mdvsocial");
        }
        if (command == null || command.isBlank()) return;
        String finalCommand = command.replaceFirst("^/", "");
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) Bukkit.dispatchCommand(player, finalCommand);
        });
    }


    private static final class BedrockRecipeGridHolder implements InventoryHolder {
        private final MdvRecipe representative;
        private final int variant;
        private final BackContext back;
        private final int size;
        private final Map<Integer, ItemSpec> ingredientSlots = new LinkedHashMap<>();
        private Inventory inventory;

        private BedrockRecipeGridHolder(MdvRecipe representative, int variant, BackContext back, int size) {
            this.representative = representative;
            this.variant = Math.max(0, variant);
            this.back = back;
            this.size = size;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        private MdvRecipe getRepresentative() {
            return representative;
        }

        private int getVariant() {
            return variant;
        }

        private BackContext getBack() {
            return back;
        }

        @SuppressWarnings("unused")
        private int getSize() {
            return size;
        }

        private Map<Integer, ItemSpec> getIngredientSlots() {
            return ingredientSlots;
        }

        private void clearIngredientSlots() {
            ingredientSlots.clear();
        }
    }

    private record CategoryInfo(String id, String name) { }
    private record FormButton(String text, Runnable action) { }
    private record IngredientLine(String name, int amount) { }
    private record InventorySearchItem(ItemStack item, int amount) { }

    private enum SearchMode { TEXT, ITEM }

    private record SearchContext(SearchMode mode, String query, ItemStack item, String label) {
        static SearchContext text(String query) {
            return new SearchContext(SearchMode.TEXT, query, null, query);
        }

        static SearchContext item(ItemStack item, String name) {
            return new SearchContext(SearchMode.ITEM, "", item == null ? null : item.clone(), name);
        }
    }

    private enum BackType { MAIN, CATEGORY, SEARCH, DETAIL }

    private record BackContext(BackType type, String category, int page, SearchContext search,
                               MdvRecipe recipe, int variant, BackContext parent) {
        static BackContext category(String category, int page) {
            return new BackContext(BackType.CATEGORY, category, page, null, null, 0, null);
        }

        static BackContext search(SearchContext search, int page) {
            return new BackContext(BackType.SEARCH, null, page, search, null, 0, null);
        }

        static BackContext detail(MdvRecipe recipe, int variant, BackContext parent) {
            return new BackContext(BackType.DETAIL, null, 0, null, recipe, variant, parent);
        }
    }
}
