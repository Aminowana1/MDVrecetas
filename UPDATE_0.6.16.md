# MDVRecetas 0.6.16

## Corrección: gemas conservan firma y prefijo

MMOItems reconstruye el `ItemStack` cuando una gema se aplica o cuando un
consumible retira una gema. Ese proceso conservaba las estadísticas nativas de
MMOItems, pero eliminaba los datos externos añadidos por MDVRecetas después del
crafteo.

La versión 0.6.16 añade un puente basado en `InventoryClickEvent` que:

- Captura el objeto forjado antes de que MMOItems procese la interacción.
- Espera al resultado final de MMOItems en prioridad `MONITOR`.
- Verifica que el resultado conserve la misma identidad MMOItems (tipo + ID).
- Restaura la firma PDC y sus líneas de lore.
- Restaura el PDC del modificador y el prefijo visible del nombre.
- Conserva intactas la gema, sus estadísticas, su lore y el resto del resultado.
- También cubre la extracción de gemas y otros consumibles de MMOItems que
  reconstruyen un objeto mediante arrastre sobre el inventario.

No realiza escaneos por tick ni revisa inventarios de forma periódica. Solo se
ejecuta durante la interacción concreta de objeto sobre objeto.

## Instalación

No requiere cambios en `config.yml`, `modifiers.yml` ni en las recetas. Basta
con reemplazar el JAR por la versión 0.6.16 y reiniciar el servidor.
