# MDVRecetas 0.3.0

Motor de recetas custom para MDVCRAFT + guía visual tipo Terraria.

## Incluye

- Carga de recetas desde `plugins/MDVRecetas/recipes/*.yml`.
- Registro de recetas Bukkit/Paper.
- Soporte para:
  - Mesa de crafteo (`SHAPED` y `SHAPELESS`).
  - Horno.
  - Alto horno.
  - Ahumador.
  - Hoguera.
- Ingredientes/resultados:
  - `VANILLA`.
  - `MMOITEMS`.
  - `ITEMSTACK` serializado en Base64.
- Reemplazo de recetas vanilla.
- XP para profesión `forjador` de MMOCore.
- Holograma flotante de XP.
- `/mdvrecetas` abre la guía visual.
- Menú principal con categorías y buscador.
- Visualizador de categorías con paginación.
- Vista completa de receta con estación visible.
- Buscador por ingrediente tipo Terraria.
- Ingredientes clickeables: si un ingrediente tiene receta visible, abre esa receta.
- `hidden: true` / `hide: true` para recetas que se pueden craftear pero no se muestran en la guía.

## Comandos

```text
/mdvrecetas
/mdvrecetas reload
/mdvrecetas debugitem
/mdvrecetas serializehand <id>
```

## Receta oculta

```yaml
recipes:
  receta_secreta:
    enabled: true
    hidden: true
    station: CRAFTING_TABLE
    category: UTILITARIOS
    type: SHAPELESS
    ingredients:
      item:
        kind: VANILLA
        material: DIAMOND
    result:
      kind: VANILLA
      material: EMERALD
      amount: 1
```

La receta seguirá funcionando en la mesa/horno correspondiente, pero no aparecerá en `/mdvrecetas`, ni en categorías, ni en el buscador.

## Flujo recomendado de prueba

1. Borra `plugins/MDVRecetas/` si estás probando desde cero.
2. Compila el jar.
3. Sube el jar al servidor.
4. Reinicia.
5. Activa una receta de ejemplo.
6. Usa `/mdvrecetas reload`.
7. Prueba `/mdvrecetas`, categorías, vista de receta y buscador.
