# MDVRecetas 0.1.0

MVP inicial de recetas custom para MDVCRAFT.

## Funciones incluidas

- Carga recetas desde `plugins/MDVRecetas/recipes/*.yml`.
- Registra recetas en Bukkit/Paper/Purpur.
- Soporta:
  - Mesa de crafteo: `SHAPED` y `SHAPELESS`.
  - Horno: `FURNACE` + `COOKING`.
  - Alto horno: `BLAST_FURNACE` + `COOKING`.
  - Ahumador: `SMOKER` + `COOKING`.
  - Hoguera: `CAMPFIRE` + `COOKING`.
- Ingredientes/resultados:
  - `VANILLA`
  - `MMOITEMS`
  - `ITEMSTACK` serializado en Base64.
- Reemplazo de recetas vanilla con `replace-vanilla`.
- XP de Forjador mediante comando de MMOCore.
- Comandos admin:
  - `/mdvrecetas reload`
  - `/mdvrecetas debugitem`
  - `/mdvrecetas serializehand <id>`

## Notas importantes de la 0.1

- En recetas `SHAPED`, cada casilla de la mesa consume 1 item. Bukkit no permite exigir `amount: 2` dentro de una sola casilla vanilla.
- En recetas `SHAPELESS`, `amount` sí se traduce repitiendo el ingrediente varias veces.
- La XP por horno/alto horno/ahumador se guarda como pendiente en el bloque y se entrega al extraer el resultado.
- CampfireRecipe se registra, pero la XP de campfire queda pendiente para una versión futura porque Bukkit no tiene un evento equivalente tan limpio con jugador extractor.
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
id: COLMILLO_ORCO
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
