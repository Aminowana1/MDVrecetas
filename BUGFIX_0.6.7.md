# MDVRecetas 0.6.7 — corrección de duplicación del slot 40

## Causa

`returnSearchItem()` leía siempre el slot configurado como centro del buscador (por defecto, 40), sin comprobar qué pantalla estaba abierta.

En la pantalla `CATEGORY`, el slot 40 también forma parte de `gui.category.recipe-slots`. Por eso, una receta mostrada allí podía ser clonada y entregada al jugador al cerrar el menú o al deshabilitar/recargar el plugin. En la configuración actual de MDVCRAFT, `BOTASORCO` usa `visual.slot: 40`.

## Corrección

- El contenido físico del slot 40 solo se recupera en las pantallas `MAIN` y `SEARCH`.
- Una sesión real de búsqueda continúa devolviendo su objeto incluso si el jugador estaba viendo una receta cuando cerró el menú.
- Los resultados e ingredientes de vista previa quedan marcados con `mdvrecetas:gui_item`.
- Durante `onDisable`, el inventario visual se limpia antes de cerrarse y la limpieza de objetos escapados se ejecuta de forma síncrona.

## Prueba recomendada

1. Abrir la categoría `ARMADURAS` donde aparezcan las Botas de Hierro Orco en el slot 40.
2. Cerrar el menú normalmente: no debe entregarse ningún objeto.
3. Abrir de nuevo el mismo menú y recargar/deshabilitar el plugin: no debe entregarse ningún objeto.
4. Colocar un objeto real en el buscador, navegar hasta una receta y cerrar: el objeto real debe regresar una sola vez.
