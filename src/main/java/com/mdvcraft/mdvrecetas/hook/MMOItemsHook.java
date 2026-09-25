package com.mdvcraft.mdvrecetas.hook;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;

public final class MMOItemsHook {
    private final boolean pluginPresent;
    private record IdentityReader(Method get, Method hasType, Method getType, Method getString) {}
    private final java.util.List<IdentityReader> identityReaders = new java.util.ArrayList<>();

    public MMOItemsHook() {
        this.pluginPresent = Bukkit.getPluginManager().isPluginEnabled("MMOItems");
        if (pluginPresent) for (String name : new String[]{"net.Indyuce.mmoitems.api.item.NBTItem",
                "io.lumine.mythic.lib.api.item.NBTItem", "net.Indyuce.mmoitems.api.item.nbt.NBTItem"}) {
            try {
                Class<?> type = Class.forName(name);
                identityReaders.add(new IdentityReader(type.getMethod("get", ItemStack.class),
                        type.getMethod("hasType"), type.getMethod("getType"), type.getMethod("getString", String.class)));
            } catch (ReflectiveOperationException ignored) { }
        }
    }

    public boolean isPluginPresent() {
        return pluginPresent;
    }

    public ItemStack buildItem(String typeId, String itemId) {
        if (!pluginPresent || typeId == null || itemId == null) {
            return null;
        }
        try {
            Class<?> mmoItemsClass = Class.forName("net.Indyuce.mmoitems.MMOItems");
            Object plugin = getStaticField(mmoItemsClass, "plugin");
            if (plugin == null) {
                return null;
            }

            Method getTypes = plugin.getClass().getMethod("getTypes");
            Object typeManager = getTypes.invoke(plugin);
            Method getType = typeManager.getClass().getMethod("get", String.class);
            Object type = getType.invoke(typeManager, typeId.toUpperCase(Locale.ROOT));
            if (type == null) {
                return null;
            }

            ItemStack baseItem = buildBaseItemWithoutModifiers(plugin, type, itemId.toUpperCase(Locale.ROOT));
            if (baseItem != null) {
                return baseItem;
            }

            Method getItem = findGetItemMethod(plugin.getClass(), type);
            if (getItem == null) {
                return null;
            }
            Object item = getItem.invoke(plugin, type, itemId.toUpperCase(Locale.ROOT));
            return item instanceof ItemStack ? (ItemStack) item : null;
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    public Optional<MmoIdentity> readIdentity(ItemStack itemStack) {
        if (!pluginPresent || itemStack == null || itemStack.getType().isAir()) {
            return Optional.empty();
        }

        for (IdentityReader reader : identityReaders) {
            Optional<MmoIdentity> identity = tryReadIdentity(reader, itemStack);
            if (identity.isPresent()) {
                return identity;
            }
        }
        return Optional.empty();
    }

    private Optional<MmoIdentity> tryReadIdentity(IdentityReader reader, ItemStack itemStack) {
        try {
            Object nbtItem = reader.get().invoke(null, itemStack);
            if (nbtItem == null) {
                return Optional.empty();
            }

            Object hasTypeResult = reader.hasType().invoke(nbtItem);
            if (!(hasTypeResult instanceof Boolean) || !((Boolean) hasTypeResult)) {
                return Optional.empty();
            }

            Object typeObject = reader.getType().invoke(nbtItem);
            String type = normalizeType(typeObject);
            if (type == null || type.isBlank()) {
                return Optional.empty();
            }

            Object idObject = reader.getString().invoke(nbtItem, "MMOITEMS_ITEM_ID");
            String id = idObject == null ? null : String.valueOf(idObject);
            if (id == null || id.isBlank()) {
                return Optional.empty();
            }

            return Optional.of(new MmoIdentity(type.toUpperCase(Locale.ROOT), id.toUpperCase(Locale.ROOT)));
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }

    /**
     * MDVRecetas wants the preview/result registered in Bukkit to look normal.
     * MMOItems#getItem may roll template modifiers depending on the item setup,
     * so we first try to build the template with the modifier group skipped.
     */
    private ItemStack buildBaseItemWithoutModifiers(Object plugin, Object type, String itemId) {
        try {
            Object templates = plugin.getClass().getMethod("getTemplates").invoke(plugin);
            Object template = null;
            for (Method method : templates.getClass().getMethods()) {
                if (!method.getName().equals("getTemplate") || method.getParameterCount() != 2) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (params[0].isInstance(type) && params[1].equals(String.class)) {
                    template = method.invoke(templates, type, itemId);
                    break;
                }
            }
            if (template == null) {
                return null;
            }

            Class<?> builderClass = Class.forName("net.Indyuce.mmoitems.api.item.build.MMOItemBuilder");
            Class<?> templateClass = Class.forName("net.Indyuce.mmoitems.api.item.template.MMOItemTemplate");
            Class<?> tierClass = Class.forName("net.Indyuce.mmoitems.api.ItemTier");
            Object builder = builderClass.getConstructor(templateClass, int.class, tierClass, boolean.class)
                    .newInstance(template, 0, null, true);
            Object mmoItem = builderClass.getMethod("build").invoke(builder);
            Object stackBuilder = mmoItem.getClass().getMethod("newBuilder").invoke(mmoItem);
            Object item = stackBuilder.getClass().getMethod("build").invoke(stackBuilder);
            return item instanceof ItemStack ? (ItemStack) item : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Method findGetItemMethod(Class<?> pluginClass, Object type) {
        for (Method method : pluginClass.getMethods()) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (!method.getName().equals("getItem") || parameterTypes.length != 2) {
                continue;
            }
            if (!parameterTypes[1].equals(String.class)) {
                continue;
            }
            if (type != null && parameterTypes[0].isInstance(type)) {
                return method;
            }
        }
        return null;
    }

    private Object getStaticField(Class<?> clazz, String fieldName) throws ReflectiveOperationException {
        Field field = clazz.getField(fieldName);
        return field.get(null);
    }

    private String normalizeType(Object typeObject) {
        if (typeObject == null) {
            return null;
        }
        if (typeObject instanceof String value) return value;
        try {
            Method getId = typeObject.getClass().getMethod("getId");
            Object id = getId.invoke(typeObject);
            if (id != null) {
                return String.valueOf(id);
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return String.valueOf(typeObject);
    }

    public record MmoIdentity(String type, String id) {
    }
}
