# MDVRecetas 0.6.19 - Visor híbrido Bedrock 3x3

- La navegación Bedrock continúa usando Forms para categorías, buscador y resultados.
- Al seleccionar una receta se abre un inventario pequeño de 27 slots, de solo lectura.
- Las recetas SHAPED muestran los 9 ingredientes en una cuadrícula física 3x3 exacta.
- Las recetas SHAPELESS muestran sus ingredientes dentro de la misma cuadrícula.
- Las recetas de cocción muestran ingrediente -> estación -> resultado.
- El resultado usa el ItemStack real de la receta.
- Los ingredientes usan los ItemStack reales (incluyendo MMOItems/ITEMSTACK cuando pueden resolverse).
- Tocar un ingrediente que también tiene receta abre su receta y conserva una pila de navegación para volver.
- Las variantes se cambian con botones Anterior/Siguiente dentro del visor.
- El botón Volver regresa al Form exacto desde el que se abrió la receta.
- Todo el visor híbrido es configurable desde `MenusBedrock/recipes.yml`.
- Java conserva su GUI original sin cambios.
- Puede desactivarse con `recipe.hybrid-inventory.enabled: false`, recuperando la vista 100% Forms de 0.6.18.
