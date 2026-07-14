package com.mdvcraft.mdvrecetas.model;

public enum MatchMode {
    TYPE,
    SIMILAR,
    EXACT,

    /**
     * Compara solamente la identidad estable de MMOItems (TYPE + ID).
     * Ignora nombre/prefijo, firma, modificadores, runas, durabilidad y demas NBT dinamico.
     */
    MMO_ID
}
