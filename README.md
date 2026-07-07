# MDVRecetas 0.3.4

Hotfix sobre 0.3.3 enfocada en recetas de horno custom con MMOItems.

## Cambios

- Mantiene el arreglo de textura custom del botón Volver.
- Cambia el sistema de XP pendiente de hornos: ya no escribe `PersistentDataContainer` del `TileState` durante `FurnaceSmeltEvent`.
- Usa memoria temporal por ubicación del horno para evitar que `TileState#update` reinicie la cocción o impida que aparezca el resultado.
- Añade `FurnaceStartSmeltEvent` para guardar qué receta MDVRecetas empezó a cocinarse. Esto ayuda cuando `FurnaceSmeltEvent#getSource()` llega sin el NBT real de MMOItems.
- Mantiene validación para evitar que un item vanilla con el mismo material base se convierta en resultado custom.

## Prueba recomendada

1. Apaga el servidor.
2. Reemplaza el jar por MDVRecetas 0.3.4.
3. Borra `plugins/MDVRecetas/` si estás probando limpio.
4. Enciende el servidor.
5. Prueba una receta de horno con un ingrediente MMOItems.
6. Si no funciona, revisa consola buscando mensajes de MDVRecetas y confirma `/mdvrecetas debugitem` del ingrediente y resultado.
