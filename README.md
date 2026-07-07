# MDVRecetas 0.3.3

Hotfix sobre 0.3.2.

## Cambios

- Boton `Volver` usa la misma estrategia de texturas que MDVSocial:
  - acepta Base64 de Minecraft Heads
  - extrae la URL real
  - aplica la textura con `SkullMeta#setOwnerProfile`
  - evita refleccion/campos internos en Paper/Purpur 1.21+

- Recetas de horno custom con MMOItems corregidas:
  - el horno sigue registrando la receta por material base para que pueda cocinar
  - al terminar, MDVRecetas valida el ItemStack real dentro del horno, no solo `FurnaceSmeltEvent#getSource()`
  - esto evita el bug donde la barra llega al final, se reinicia y no entrega resultado
  - si alguien intenta cocinar un item vanilla con el mismo material base que un ingrediente custom, se cancela para no regalar resultados custom

## Receta de horno ejemplo

```yml
recipes:
  hierro_orco_refinado:
    enabled: true
    station: FURNACE
    category: MATERIALES
    hidden: false
    type: COOKING

    ingredient:
      kind: MMOITEMS
      type: MATERIAL
      id: LINGOTEORCO

    result:
      kind: MMOITEMS
      type: MATERIAL
      id: HIERROORCO
      amount: 1

    cooking:
      time: 200
      vanilla-exp: 0.2

    forjador:
      exp: 50
```

`forjador.exp: 50` sirve para testeo, pero para balance real conviene bajarlo.
