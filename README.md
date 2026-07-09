# MDVRecetas 0.6.6

Motor de recetas custom para MDVCRAFT.

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
