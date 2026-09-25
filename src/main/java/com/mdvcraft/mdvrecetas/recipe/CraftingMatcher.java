package com.mdvcraft.mdvrecetas.recipe;

import com.mdvcraft.mdvrecetas.model.*;
import com.mdvcraft.mdvrecetas.service.ItemResolver;
import org.bukkit.inventory.ItemStack;
import java.util.*;
import java.util.function.BiPredicate;

/** Compiled ingredient layout; rebuilt only when the recipe registry changes. */
final class CraftingMatcher {
    private record Candidate(MdvRecipe recipe, int mask, ItemSpec[] ingredients) {}
    private final Map<Integer, List<Candidate>> byCount = new HashMap<>();
    private final ItemResolver resolver;

    CraftingMatcher(Collection<MdvRecipe> recipes, ItemResolver resolver) {
        this.resolver = resolver;
        for (MdvRecipe recipe : recipes) {
            if (recipe.getStation() != StationType.CRAFTING_TABLE || recipe.getType() == RecipeType.COOKING) continue;
            int mask = 0;
            List<ItemSpec> ingredients = new ArrayList<>();
            if (recipe.getType() == RecipeType.SHAPED) {
                for (int row = 0; row < 3; row++) {
                    String line = row < recipe.getShape().size() ? recipe.getShape().get(row) : "";
                    for (int col = 0; col < 3; col++) {
                        char symbol = col < line.length() ? line.charAt(col) : ' ';
                        if (symbol != ' ') {
                            mask |= 1 << (row * 3 + col);
                            ingredients.add(recipe.getShapedIngredients().get(symbol));
                        }
                    }
                }
            } else {
                mask = -1;
                for (ItemSpec spec : recipe.getShapelessIngredients().values()) {
                    // Bukkit permits at most nine occupied crafting slots.
                    for (int n = 0; n < spec.getAmount() && ingredients.size() <= 9; n++) ingredients.add(spec);
                    if (ingredients.size() > 9) break;
                }
            }
            if (ingredients.size() > 9 || ingredients.contains(null)) continue;
            byCount.computeIfAbsent(ingredients.size(), ignored -> new ArrayList<>())
                    .add(new Candidate(recipe, mask, ingredients.toArray(ItemSpec[]::new)));
        }
    }

    Optional<MdvRecipe> find(ItemStack[] raw) {
        if (raw == null || (raw.length != 4 && raw.length != 9)) return Optional.empty();
        int mask = 0;
        List<ItemStack> actual = new ArrayList<>(9);
        for (int i = 0; i < raw.length; i++) {
            ItemStack item = raw[i];
            if (item == null || item.getType().isAir()) continue;
            int slot = raw.length == 4 ? (i / 2) * 3 + i % 2 : i;
            mask |= 1 << slot;
            actual.add(item);
        }
        if (actual.isEmpty()) return Optional.empty();
        BiPredicate<ItemStack, ItemSpec> matches = resolver.newMatchContext();
        for (Candidate candidate : byCount.getOrDefault(actual.size(), List.of())) {
            if (candidate.mask() != -1 && candidate.mask() != mask) continue;
            if (candidate.mask() == -1) {
                if (shapeless(candidate.ingredients(), actual, matches)) return Optional.of(candidate.recipe());
            } else {
                boolean valid = true;
                for (int i = 0; i < actual.size(); i++) {
                    ItemSpec expected = candidate.ingredients()[i];
                    if (actual.get(i).getAmount() < expected.getAmount() || !matches.test(actual.get(i), expected)) {
                        valid = false; break;
                    }
                }
                if (valid) return Optional.of(candidate.recipe());
            }
        }
        return Optional.empty();
    }

    private boolean shapeless(ItemSpec[] expected, List<ItemStack> actual, BiPredicate<ItemStack, ItemSpec> matches) {
        boolean[][] compatible = new boolean[expected.length][actual.size()];
        for (int e = 0; e < expected.length; e++)
            for (int a = 0; a < actual.size(); a++) compatible[e][a] = matches.test(actual.get(a), expected[e]);
        int[] assigned = new int[actual.size()];
        Arrays.fill(assigned, -1);
        for (int e = 0; e < expected.length; e++)
            if (!assign(e, compatible, assigned, new boolean[actual.size()])) return false;
        return true;
    }

    private boolean assign(int expected, boolean[][] compatible, int[] assigned, boolean[] visited) {
        for (int a = 0; a < assigned.length; a++) {
            if (!compatible[expected][a] || visited[a]) continue;
            visited[a] = true;
            if (assigned[a] == -1 || assign(assigned[a], compatible, assigned, visited)) {
                assigned[a] = expected; return true;
            }
        }
        return false;
    }
}
