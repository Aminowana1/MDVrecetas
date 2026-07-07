# MDVRecetas 0.2.0

Motor de recetas custom para MDVCRAFT + visualizador tipo Terraria.

## Funciones incluidas

- Carga recetas desde `plugins/MDVRecetas/recipes/*.yml`.
- Registra recetas en Bukkit/Paper/Purpur.
- Soporta:
  - Mesa de crafteo: `SHAPED` y `SHAPELESS`.
  - Horno: `FURNACE` + `COOKING`.
  - Alto horno: `BLAST_FURNACE` + `COOKING`.
  - Ahumador: `SMOKER` + `COOKING`.
  - Hoguera: `CAMPFIRE` + `COOKING` registrada, aunque no recomendada todavía para XP.
- Ingredientes/resultados:
  - `VANILLA`.
  - `MMOITEMS`.
  - `ITEMSTACK` serializado en Base64.
- Reemplazo de recetas vanilla con `replace-vanilla`.
- XP de Forjador mediante comando de MMOCore.
- Holograma flotante de XP sobre la mesa/estación usando `TextDisplay`.
- Visualizador `/mdvrecetas`:
  - Categorías.
  - Lista paginada de recetas.
  - Vista de receta completa.
  - Buscador por ingrediente tipo Terraria.
  - Muestra la estación donde se fabrica cada item.
- Comandos:
  - `/mdvrecetas` abre la guía.
  - `/mdvrecetas reload` recarga recetas.
  - `/mdvrecetas debugitem` revisa el item en mano.
  - `/mdvrecetas serializehand <id>` guarda un ItemStack exacto.

## Notas importantes

- En recetas `SHAPED`, cada casilla de la mesa consume 1 item. Bukkit no permite exigir `amount: 2` dentro de una sola casilla de mesa vanilla.
- En recetas `SHAPELESS`, `amount` sí se traduce repitiendo el ingrediente varias veces.
- La XP por horno/alto horno/ahumador se guarda como pendiente en el bloque y se entrega al extraer el resultado.
- CampfireRecipe se registra, pero la XP de campfire queda pendiente para una versión posterior porque Bukkit no tiene un evento equivalente tan limpio con jugador extractor.
- Los modifiers, firma de crafteo y editor admin quedan para futuras versiones.

## Formato de item

### Vanilla

```yml
kind: VANILLA
material: STICK
amount: 1
```

### MMOItems

```yml
kind: MMOITEMS
type: MATERIAL
id: COLMILLOORCO
amount: 1
```

### ItemStack serializado

Usa:

```txt
/mdvrecetas serializehand mi_item
```

El plugin guarda el item en:

```txt
plugins/MDVRecetas/serialized-items/mi_item.yml
```

Luego puedes copiar el bloque `item:` dentro de una receta.

## Buscador tipo Terraria

Abre `/mdvrecetas`, pon un objeto en el centro del cuadro inferior izquierdo y el menú mostrará todas las recetas que usan ese item como ingrediente. Al cerrar el menú o cambiar de categoría, el item vuelve al inventario del jugador.
