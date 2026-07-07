package com.mdvcraft.mdvrecetas.model;

public final class ForjadorOptions {
    private final double exp;
    private final boolean signature;
    private final boolean modifiers;
    private final String modifierPool;

    public ForjadorOptions(double exp, boolean signature, boolean modifiers, String modifierPool) {
        this.exp = exp;
        this.signature = signature;
        this.modifiers = modifiers;
        this.modifierPool = modifierPool;
    }

    public double getExp() {
        return exp;
    }

    public boolean isSignature() {
        return signature;
    }

    public boolean isModifiers() {
        return modifiers;
    }

    public String getModifierPool() {
        return modifierPool;
    }
}
