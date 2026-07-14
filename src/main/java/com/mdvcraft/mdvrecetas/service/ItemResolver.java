package com.mdvcraft.mdvrecetas.service;

import com.mdvcraft.mdvrecetas.hook.MMOItemsHook;
import com.mdvcraft.mdvrecetas.model.ItemKind;
import com.mdvcraft.mdvrecetas.model.ItemSpec;
import com.mdvcraft.mdvrecetas.model.MatchMode;
import com.mdvcraft.mdvrecetas.util.ItemStackSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;
import java.util.Optional;

public final class ItemResolver {
    private final MMOItemsHook mmoItemsHook;

    public ItemResolver(MMOItemsHook mmoItemsHook) {
        this.mmoItemsHook = mmoItemsHook;
    }

    public MMOItemsHook getMmoItemsHook() {
        return mmoItemsHook;
    }

    public ItemSpec fromConfig(ConfigurationSection section) {
        if (section == null) {
            throw new IllegalArgumentException("Item section is missing");
        }

        ItemKind kind = enumValue(ItemKind.class, section.getString("kind", "VANILLA"), ItemKind.VANILLA);
        int amount = section.getInt("amount", 1);
        MatchMode matchMode = enumValue(MatchMode.class, section.getString("match", defaultMatch(kind)), defaultMatchMode(kind));

        switch (kind) {
            case VANILLA -> {
                Material material = Material.matchMaterial(section.getString("material", "AIR"));
                if (material == null || material.isAir()) {
                    throw new IllegalArgumentException("Invalid vanilla material: " + section.getString("material"));
                }
                return new ItemSpec(kind, material, null, null, amount, matchMode, null);
            }
            case MMOITEMS -> {
                String type = section.getString("type");
                String id = section.getString("id");
                if (type == null || type.isBlank() || id == null || id.isBlank()) {
                    throw new IllegalArgumentException("MMOItems item requires type and id");
                }
                return new ItemSpec(kind, null, type.toUpperCase(Locale.ROOT), id.toUpperCase(Locale.ROOT), amount, matchMode, null);
            }
            case ITEMSTACK -> {
                String data = section.getString("data");
                if (data == null || data.isBlank()) {
                    throw new IllegalArgumentException("ITEMSTACK item requires data");
                }
                return new ItemSpec(kind, null, null, null, amount, matchMode, data);
            }
            default -> throw new IllegalArgumentException("Unsupported item kind: " + kind);
        }
    }

    public ItemStack buildItem(ItemSpec spec) {
        if (spec == null) {
            return null;
        }

        ItemStack itemStack;
        switch (spec.getKind()) {
            case VANILLA -> itemStack = new ItemStack(spec.getMaterial());
            case MMOITEMS -> itemStack = mmoItemsHook.buildItem(spec.getMmoType(), spec.getMmoId());
            case ITEMSTACK -> {
                try {
                    itemStack = ItemStackSerializer.fromBase64(spec.getData());
                } catch (Exception exception) {
                    throw new IllegalArgumentException("Could not deserialize ITEMSTACK data", exception);
                }
            }
            default -> itemStack = null;
        }

        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        itemStack = itemStack.clone();
        itemStack.setAmount(Math.max(1, spec.getAmount()));
        return itemStack;
    }

    public RecipeChoice buildChoice(ItemSpec spec) {
        if (spec == null) {
            return null;
        }
        if (spec.getKind() == ItemKind.VANILLA && spec.getMatchMode() == MatchMode.TYPE) {
            return new RecipeChoice.MaterialChoice(spec.getMaterial());
        }

        // Bukkit no puede expresar "mismo TYPE + ID de MMOItems ignorando NBT dinamico".
        // Registramos solo el material para que la mesa reconozca el patron y luego
        // RecipeCraftListener valida la identidad real antes de mostrar/entregar resultado.
        if (spec.getKind() == ItemKind.MMOITEMS && spec.getMatchMode() == MatchMode.MMO_ID) {
            ItemStack representative = buildItem(spec);
            if (representative == null || representative.getType().isAir()) {
                return null;
            }
            return new RecipeChoice.MaterialChoice(representative.getType());
        }

        ItemStack exact = buildItem(spec);
        if (exact == null || exact.getType().isAir()) {
            return null;
        }
        exact.setAmount(1);
        return new RecipeChoice.ExactChoice(exact);
    }


    /**
     * Cooking recipes in Bukkit/Minecraft are much more reliable when their
     * ingredient is registered by material. ExactChoice can make custom-NBT
     * items start cooking but fail/reset at the end on some server builds.
     *
     * MDVRecetas still validates the real custom item in FurnaceSmeltEvent
     * with matches(...), so this is only the material-level trigger that lets
     * the furnace process run.
     */
    public RecipeChoice buildCookingChoice(ItemSpec spec) {
        if (spec == null) {
            return null;
        }
        if (spec.getKind() == ItemKind.VANILLA) {
            return new RecipeChoice.MaterialChoice(spec.getMaterial());
        }
        ItemStack item = buildItem(spec);
        if (item == null || item.getType().isAir()) {
            return null;
        }
        return new RecipeChoice.MaterialChoice(item.getType());
    }

    public boolean matches(ItemStack itemStack, ItemSpec spec) {
        if (itemStack == null || itemStack.getType().isAir() || spec == null) {
            return false;
        }

        return switch (spec.getKind()) {
            case VANILLA -> itemStack.getType() == spec.getMaterial();
            case MMOITEMS -> {
                if (spec.getMatchMode() == MatchMode.EXACT || spec.getMatchMode() == MatchMode.SIMILAR) {
                    ItemStack target = buildItem(spec);
                    if (target == null) {
                        yield false;
                    }
                    target.setAmount(itemStack.getAmount());
                    yield spec.getMatchMode() == MatchMode.EXACT
                            ? itemStack.equals(target)
                            : itemStack.isSimilar(target);
                }
                Optional<MMOItemsHook.MmoIdentity> identity = mmoItemsHook.readIdentity(itemStack);
                yield identity
                        .filter(value -> value.type().equalsIgnoreCase(spec.getMmoType()) && value.id().equalsIgnoreCase(spec.getMmoId()))
                        .isPresent();
            }
            case ITEMSTACK -> {
                ItemStack target = buildItem(spec);
                if (target == null) {
                    yield false;
                }
                target.setAmount(itemStack.getAmount());
                if (spec.getMatchMode() == MatchMode.EXACT) {
                    yield itemStack.equals(target);
                }
                yield itemStack.isSimilar(target);
            }
        };
    }

    public String describe(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return "AIR";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Material: ").append(itemStack.getType()).append("\n");
        builder.append("Amount: ").append(itemStack.getAmount()).append("\n");

        Optional<MMOItemsHook.MmoIdentity> identity = mmoItemsHook.readIdentity(itemStack);
        if (identity.isPresent()) {
            builder.append("Kind: MMOITEMS\n");
            builder.append("MMOItems Type: ").append(identity.get().type()).append("\n");
            builder.append("MMOItems ID: ").append(identity.get().id()).append("\n");
        } else if (isCleanVanilla(itemStack)) {
            builder.append("Kind: VANILLA\n");
            builder.append("YAML:\n");
            builder.append("kind: VANILLA\n");
            builder.append("material: ").append(itemStack.getType()).append("\n");
        } else {
            builder.append("Kind: ITEMSTACK\n");
            builder.append("YAML recomendado: usa /mdvrecetas serializehand <id>\n");
        }
        return builder.toString();
    }

    public boolean isCleanVanilla(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return false;
        }
        ItemMeta meta = itemStack.getItemMeta();
        return meta == null || (!meta.hasDisplayName()
                && !meta.hasLore()
                && !meta.hasEnchants()
                && !meta.hasCustomModelData()
                && meta.getPersistentDataContainer().getKeys().isEmpty());
    }

    private <T extends Enum<T>> T enumValue(Class<T> enumClass, String raw, T fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(enumClass, raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private String defaultMatch(ItemKind kind) {
        return defaultMatchMode(kind).name();
    }

    private MatchMode defaultMatchMode(ItemKind kind) {
        if (kind == ItemKind.VANILLA) {
            return MatchMode.TYPE;
        }
        if (kind == ItemKind.MMOITEMS) {
            return MatchMode.SIMILAR;
        }
        return MatchMode.SIMILAR;
    }
}
