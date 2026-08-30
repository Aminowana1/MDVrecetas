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
 * Java conserva RecipeGuiManager sin cambios. En Bedrock se usan Forms para:
 * - categorías;
 * - lista paginada de recetas;
 * - vista completa de una receta y sus variantes;
 * - buscador por texto;
 * - buscador exacto por el objeto sostenido en la mano.
 */
public final class BedrockRecipeMenuManager {
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
        ItemStack hand = player.getInventory().getItemInMainHand();
        String handName = isEmpty(hand) ? text("search.none", "Nada", Map.of()) : displayName(hand);
        Map<String, String> values = values("hand", handName);
        String title = text("search.menu.title", "&8&lBuscador de Recetas", values);
        String content = join(lines("search.menu.content", List.of(
                "&7Puedes escribir el nombre de un objeto o buscar recetas que usen exactamente el objeto que sostienes.",
                "",
                "&7Objeto en mano: &e{hand}"
        ), values));

        List<FormButton> buttons = new ArrayList<>();
        buttons.add(new FormButton(text("search.menu.text-search.text", "&b&lBuscar por nombre\n&r&7Escribe objeto, ingrediente o receta.", values),
                () -> openTextSearchInput(player)));
        buttons.add(new FormButton(text("search.menu.hand-search.text", "&a&lUsar objeto en mano\n&r&7Encuentra recetas que utilizan {hand}.", values),
                () -> searchWithHand(player)));
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

    private void searchWithHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (isEmpty(hand)) {
            socialHook.play(player, "invalid");
            Map<String, String> values = values("message", text("messages.empty-hand", "&cDebes sostener un objeto en la mano principal.", Map.of()));
            sendSimpleForm(player,
                    text("search.error.title", "&8&lBuscador", values),
                    ColorUtil.color(values.get("message")),
                    List.of(new FormButton(text("search.error.back.text", "&6Volver", values), () -> openSearchMenu(player))));
            return;
        }
        openSearchResults(player, SearchContext.hand(hand.clone(), displayName(hand)), 1);
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
        if (context.mode() == SearchMode.HAND) {
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
                "&e&l● &7&lIngredientes",
                "{ingredients}",
                "{extra}"
        ), values);
        List<String> ingredientLines = ingredientLines(recipe);
        List<String> extraLines = extraRecipeLines(recipe, values);
        List<String> contentLines = expandRecipeTemplates(templates, values, ingredientLines, extraLines);

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
                                               List<String> ingredients, List<String> extra) {
        List<String> result = new ArrayList<>();
        for (String raw : templates) {
            String marker = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
            switch (marker) {
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
                current.setDefaults(defaults);
                current.options().copyDefaults(true);
                current.save(file);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("No se pudo preparar " + MENU_RESOURCE + ": " + ex.getMessage());
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

    private record CategoryInfo(String id, String name) { }
    private record FormButton(String text, Runnable action) { }
    private record IngredientLine(String name, int amount) { }

    private enum SearchMode { TEXT, HAND }

    private record SearchContext(SearchMode mode, String query, ItemStack item, String label) {
        static SearchContext text(String query) {
            return new SearchContext(SearchMode.TEXT, query, null, query);
        }

        static SearchContext hand(ItemStack item, String name) {
            return new SearchContext(SearchMode.HAND, "", item == null ? null : item.clone(), name);
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
