# MDVRecetas 0.6.13

Motor de recetas custom para MDVCRAFT.

## Nuevo en 0.6.13

- Los hologramas de XP de Forjador se fusionan dentro de un radio configurable.
- En vez de crear muchos `TextDisplay`, los premios cercanos reutilizan uno solo.
- La XP puede acumularse visualmente: 64 premios de 1 XP muestran un único `+64 EXP!`.
- El tiempo de desaparición se renueva con cada premio nuevo.
- Los hologramas de MDVRecetas se etiquetan y limpian en reload/apagado sin tocar los de otros plugins.

Configuración nueva, retrocompatible:

```yaml
forjador:
  hologram:
    single-per-radius: true
    merge-radius: 8.0
    accumulate-xp: true
```

## Nuevo en 0.6.12

- `mdvrecetas.editor` permite crear recetas nuevas con `/mdvrecetas editor`.
- No permite abrir el catálogo admin, editar, eliminar ni sobrescribir recetas existentes.
- `mdvrecetas.admin` conserva las acciones administrativas completas.

## Nuevo en 0.6.5

- Fix visual: los botones/paneles del menú ahora se marcan como items internos de GUI y se limpian si por bug quedan en el cursor o inventario del jugador al cerrar.
- Posición manual opcional dentro del visualizador de categoría.
- Recetas vinculadas opcionales: varias recetas pueden mostrarse como una sola entrada y la vista de receta cambia sus ingredientes automáticamente cada 1.5s.
- Todo es retrocompatible: las recetas que no usen `visual:` siguen funcionando y ordenándose como antes.

## Posición manual en categoría

Sirve solo para el visualizador dentro de una categoría. La página es 1-based, o sea `page: 1` es la primera página.

```yaml
recipes:
  ejemplo_item:
    enabled: true
    station: CRAFTING_TABLE
    category: MATERIALES
    type: SHAPED
    visual:
      page: 2
      slot: 14
    # resto de la receta...
```

Si una receta no tiene `visual.page` / `visual.slot`, MDVRecetas la coloca automáticamente como antes. Los items automáticos no pisan slots reservados por recetas con posición manual.

También acepta aliases por compatibilidad:

```yaml
display:
  page: 2
  slot: 14
```

## Recetas vinculadas

Usa el mismo `visual.group` en todas las recetas que quieras mostrar como una sola entrada. La receta marcada como `primary: true` será la principal en la categoría. En la vista de receta, los ingredientes van rotando.

Ejemplo: varios troncos distintos producen el mismo cargamento.

```yaml
recipes:
  cargamento_lena_roble:
    enabled: true
    station: CRAFTING_TABLE
    category: MATERIALES
    type: SHAPELESS
    visual:
      group: cargamento_lena
      primary: true
      page: 1
      slot: 14
    ingredients:
      item_1:
        kind: VANILLA
        material: OAK_LOG
        amount: 9
    result:
      kind: MMOITEMS
      type: MATERIAL
      id: CARGAMENTOLENA
      amount: 1

  cargamento_lena_abeto:
    enabled: true
    station: CRAFTING_TABLE
    category: MATERIALES
    type: SHAPELESS
    visual:
      group: cargamento_lena
    ingredients:
      item_1:
        kind: VANILLA
        material: SPRUCE_LOG
        amount: 9
    result:
      kind: MMOITEMS
      type: MATERIAL
      id: CARGAMENTOLENA
      amount: 1
```

La entrada aparece una sola vez. Al abrirla, la receta cambia cada `gui.recipe.linked-cycle-ticks`, por defecto 30 ticks = 1.5s.

## Firma

```yaml
signature:
  enabled: true
  lore-lines:
    - ''
    - '&a &7🔨 &l&aForjado por: &e%player%'
```

## Modificadores por Forjador

Ahora se configuran en:

```text
plugins/MDVRecetas/modifiers.yml
```

Ejemplo:

```yaml
forjador-modifiers:
  enabled: true

  chances:
    level-1:
      bad: 40
      normal: 50
      good: 9
      very-good: 1
    level-50:
      bad: 5
      normal: 45
      good: 35
      very-good: 15

  qualities:
    bad:
      - t1_tanque_oxidado
    normal:
      - t1_normal
    good:
      - t1_tanque_pulido
    very-good:
      - t1_tanque_reforzado
    blocked:
      - reliquia
      - simbionte
```

Si un prefix no sale automáticamente, puedes forzarlo así:

```yaml
forjador-modifiers:
  prefix-overrides:
    t1_tanque_oxidado: '&8Oxidado'
```

## Recetas con modifiers

```yaml
forjador:
  exp: 2.0
  signature: true
  modifiers: true
```

## Comandos

- `/mdvrecetas`
- `/mdvrecetas admin`
- `/mdvrecetas editor`
- `/mdvrecetas reload`
- `/mdvrecetas debugitem`
- `/mdvrecetas debugforjador`
- `/mdvrecetas serializehand <id>`

## Placeholders disponibles

```text
%mdvrecetas_forjador_nivel%
%mdvrecetas_forjador_chance_danado%
%mdvrecetas_forjador_chance_estable%
%mdvrecetas_forjador_chance_refinado%
%mdvrecetas_forjador_chance_magistral%
%mdvrecetas_forjador_chance_bad%
%mdvrecetas_forjador_chance_normal%
%mdvrecetas_forjador_chance_good%
%mdvrecetas_forjador_chance_very_good%
%mdvrecetas_forjador_probabilidades_1%
%mdvrecetas_forjador_probabilidades_2%
%mdvrecetas_forjador_probabilidades%
```

Nombres roleros usados:

```text
Dañado = bad
Estable = normal
Refinado = good
Magistral = very-good
```


## Fix en 0.6.6

- Refuerzo del limpiador de items internos de GUI.
- Si un panel decorativo escapa al cursor, inventario o se dropea al cerrar con inventario lleno, se elimina automáticamente.
- Bloqueo de pickup de items internos de GUI marcados por MDVRecetas.


## Fix en 0.6.7

- Corrige una duplicación grave de objetos del visualizador de recetas.
- El slot 40 solo se devuelve al jugador cuando pertenece realmente al buscador (`MAIN`/`SEARCH`).
- Los resultados e ingredientes mostrados como vista previa ahora se marcan como objetos internos de GUI.
- Al deshabilitar o recargar el plugin, el inventario visual se limpia de forma síncrona antes de cerrarse.
- Evita que recetas colocadas visualmente en el slot 40, como `BOTASORCO`, sean entregadas gratis al cerrar o recargar.


## MDVRecetas 0.6.8 - MMOItems dinámicos y archivos del editor

Los ingredientes MMOItems aceptan `match: MMO_ID`. Este modo compara solamente `type + id` y por eso ignora datos dinámicos como firma de crafteo, prefijos/modificadores, durabilidad actual, runas y otros NBT agregados al objeto.

```yaml
ingredients:
  A:
    kind: MMOITEMS
    type: TOOL
    id: PICOTA_VIRIDITA
    amount: 1
    match: MMO_ID
```

Modos disponibles:
- `MMO_ID`: identidad estable TYPE + ID; recomendado para herramientas usadas como ingrediente.
- `SIMILAR`: exige que el meta sea similar al objeto base.
- `EXACT`: exige igualdad completa.
- `TYPE`: material vanilla.

El editor guarda automáticamente los MMOItems nuevos con `match: MMO_ID`. En Opciones de receta aparece **Archivo de guardado**. El selector lista todos los `.yml` y `.yaml` existentes dentro de `plugins/MDVRecetas/recipes/`, incluyendo subcarpetas. Para crear una categoría de archivo basta crear, por ejemplo, `MaterialesRecetas.yml` y recargar MDVRecetas; después aparecerá en el editor.
