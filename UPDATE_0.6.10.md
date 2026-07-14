# MDVRecetas 0.6.10

## Movimiento seguro de recetas entre archivos

- Al cambiar el archivo de guardado de una receta existente, primero se copia el nodo completo al destino.
- El destino se guarda mediante archivo temporal y reemplazo atómico cuando el sistema lo permite.
- Después se vuelve a cargar el archivo de destino y se verifica que la receta exista.
- Solo tras esa verificación se elimina la receta del archivo de origen.
- Si falla la eliminación del origen, puede quedar una copia duplicada, pero nunca se pierde la receta.
- Se preservan claves que el editor no administra directamente, incluidas opciones visuales, grupos y campos futuros/custom.
- Si solo se edita una receta sin cambiarla de archivo, también se usa guardado temporal seguro.

No requiere borrar `plugins/MDVRecetas/`, recetas ni datos existentes.
