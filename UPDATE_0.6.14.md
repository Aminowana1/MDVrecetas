# MDVRecetas 0.6.14

## Compatibilidad con MMOItems Revision ID

- Conserva la firma `Forjado por` al actualizar un MMOItem por Revision ID.
- Conserva los PDC internos de la firma: UUID, nombre del creador e ID de receta.
- Conserva el prefijo visible de calidad aplicado por MDVRecetas, por ejemplo `Dañado`, `Gastado`, `Afinado` o `Magistral`.
- Mantiene el nombre base nuevo de la plantilla de MMOItems; solo vuelve a anteponer el prefijo conservado.
- Conserva los PDC internos del modificador de Forjador.
- Los nuevos crafteos almacenan también el prefijo exacto usado para poder restaurarlo en revisiones futuras.
- Los objetos antiguos sin ese nuevo PDC usan como fallback `forjador-modifiers.prefix-overrides.<modifier-id>`.

La compatibilidad se engancha al evento final de reconstrucción de MMOItems mediante reflexión, por lo que el proyecto sigue sin requerir MMOItems-API como dependencia de compilación.
