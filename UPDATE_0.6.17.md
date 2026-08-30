# MDVRecetas 0.6.17 - Visualizador Bedrock

- Añade Forms nativos Floodgate para `/recetas` y `/mdvrecetas` en jugadores Bedrock.
- Mantiene exactamente el GUI de inventario existente para jugadores Java.
- Categorías y resultados paginados para móvil, consola y PC Bedrock.
- Vista de receta completa con ingredientes, estación, tipo, resultado, EXP de Forjador y datos de cocción.
- Soporta recetas con `visual-group` mediante navegación entre variantes.
- Mantiene navegación a recetas de ingredientes que también sean fabricables.
- Buscador Bedrock por texto (resultado, ingrediente, ID, categoría) y por objeto en mano.
- El buscador por objeto en mano replica el comportamiento del centro del GUI Java sin mover el objeto del inventario.
- Toda la interfaz Bedrock es editable en `plugins/MDVRecetas/MenusBedrock/recipes.yml`.
- El YAML se autoactualiza añadiendo claves nuevas sin reemplazar personalizaciones existentes.
