package com.mdvcraft.mdvrecetas.hook;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;

public final class MMOItemsHook {
    private final boolean pluginPresent;

    public MMOItemsHook() {
        this.pluginPresent = Bukkit.getPluginManager().isPluginEnabled("MMOItems");
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

        for (String className : new String[]{
                "net.Indyuce.mmoitems.api.item.NBTItem",
                "io.lumine.mythic.lib.api.item.NBTItem",
                "net.Indyuce.mmoitems.api.item.nbt.NBTItem"
        }) {
            Optional<MmoIdentity> identity = tryReadIdentity(className, itemStack);
            if (identity.isPresent()) {
                return identity;
            }
        }
        return Optional.empty();
    }

    private Optional<MmoIdentity> tryReadIdentity(String className, ItemStack itemStack) {
        try {
            Class<?> nbtItemClass = Class.forName(className);
            Method get = nbtItemClass.getMethod("get", ItemStack.class);
            Object nbtItem = get.invoke(null, itemStack);
            if (nbtItem == null) {
                return Optional.empty();
            }

            Method hasType = nbtItemClass.getMethod("hasType");
            Object hasTypeResult = hasType.invoke(nbtItem);
            if (!(hasTypeResult instanceof Boolean) || !((Boolean) hasTypeResult)) {
                return Optional.empty();
            }

            Method getType = nbtItemClass.getMethod("getType");
            Object typeObject = getType.invoke(nbtItem);
            String type = normalizeType(typeObject);
            if (type == null || type.isBlank()) {
                return Optional.empty();
            }

            Method getString = nbtItemClass.getMethod("getString", String.class);
            Object idObject = getString.invoke(nbtItem, "MMOITEMS_ITEM_ID");
            String id = idObject == null ? null : String.valueOf(idObject);
            if (id == null || id.isBlank()) {
                return Optional.empty();
            }

            return Optional.of(new MmoIdentity(type.toUpperCase(Locale.ROOT), id.toUpperCase(Locale.ROOT)));
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
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
