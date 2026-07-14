# MDVRecetas 0.6.8

## Ingredientes MMOItems dinámicos

- Nuevo modo `match: MMO_ID`.
- Compara únicamente TYPE + ID de MMOItems.
- Ignora firma, nombre/prefijo, modificadores, durabilidad actual, runas y NBT dinámico.
- El editor guarda MMOItems con `match: MMO_ID` automáticamente.
- `PrepareItemCraftEvent` valida la matriz real antes de mostrar resultado, evitando que un objeto del mismo material pero de otro ID active la receta.

## Archivo de guardado desde el editor

- Nuevo botón `Archivo de guardado` en Opciones.
- Click izquierdo/derecho recorre todos los `.yml/.yaml` de la carpeta de recetas.
- Las recetas editadas conservan su archivo original por defecto.
- Config opcional: `editor.default-save-file: editor.yml`.

## Ejemplo

```yaml
kind: MMOITEMS
type: TOOL
id: PICOTA_VIRIDITA
amount: 1
match: MMO_ID
```
