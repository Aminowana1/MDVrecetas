package com.mdvcraft.mdvrecetas.model;

import org.bukkit.Material;

public final class ItemSpec {
    private final ItemKind kind;
    private final Material material;
    private final String mmoType;
    private final String mmoId;
    private final int amount;
    private final MatchMode matchMode;
    private final String data;

    public ItemSpec(ItemKind kind, Material material, String mmoType, String mmoId, int amount, MatchMode matchMode, String data) {
        this.kind = kind;
        this.material = material;
        this.mmoType = mmoType;
        this.mmoId = mmoId;
        this.amount = Math.max(1, amount);
        this.matchMode = matchMode == null ? MatchMode.SIMILAR : matchMode;
        this.data = data;
    }

    public ItemKind getKind() {
        return kind;
    }

    public Material getMaterial() {
        return material;
    }

    public String getMmoType() {
        return mmoType;
    }

    public String getMmoId() {
        return mmoId;
    }

    public int getAmount() {
        return amount;
    }

    public MatchMode getMatchMode() {
        return matchMode;
    }

    public String getData() {
        return data;
    }
}
