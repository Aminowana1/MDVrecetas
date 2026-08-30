# MDVRecetas 0.6.18 - Buscador de inventario + forma 3x3 Bedrock

- Corrige el buscador Bedrock para no depender del objeto de la mano principal.
- El botón de búsqueda ahora abre un selector paginado con los objetos reales del inventario del jugador.
- Por defecto se excluye el slot de la mano principal, por lo que el Libro del Aventurero usado para abrir el menú no aparece en el selector.
- Se conserva también la búsqueda por texto.
- Las recetas `SHAPED` ahora muestran la forma exacta 3x3 de la mesa de crafteo usando símbolos y una leyenda de ingredientes.
- Las recetas `SHAPELESS` indican claramente que no tienen forma fija.
- Las recetas de cocción muestran visualmente `ingrediente → resultado`.
- Los textos y el formato siguen siendo editables desde `MenusBedrock/recipes.yml`.
- Incluye migración segura de los valores por defecto de 0.6.17: solo reemplaza textos antiguos cuando coinciden exactamente con los defaults, sin pisar personalizaciones.
- Los menús Java no se modifican.
