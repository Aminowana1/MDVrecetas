# MDVRecetas 0.6.20 - Recarga incremental sin congelar el servidor

## Cambios

- `/mdvrecetas reload` ya no elimina y vuelve a registrar todas las recetas.
- Se comparan las recetas del YAML con las que ya están cargadas:
  - sin cambios: no se toca Bukkit;
  - nueva: se registra solo esa receta;
  - modificada: se reemplaza solo esa receta;
  - eliminada/deshabilitada: se quita solo esa receta.
- Crear o editar desde el editor sincroniza únicamente la receta guardada.
- Eliminar desde el editor quita únicamente la receta eliminada.
- El orden visual de los menús no cambia; sigue dependiendo de `displayComparator()` y de la metadata visual existente.
- La lectura/comparación de YAML se separó del registro de Bukkit en clases dedicadas:
  - `RecipeDiskScanner`
  - `RecipeDiskEntry`
  - `RecipeReloadService`

## Motivo

En Purpur/Paper 1.21.6 cada `Bukkit.addRecipe(...)` puede provocar trabajo costoso de recursos/advancements.
La versión anterior hacía ese proceso para todas las recetas incluso cuando solo se editaba una.
