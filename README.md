# MDVRecetas 0.4.0

Motor de recetas custom para MDVCRAFT con guía visual tipo Terraria y editor admin básico.

## Nuevo en 0.4.0

- Limpieza de memoria temporal de hornos al romperse o explotar.
- `/mdvrecetas editor` para crear recetas dentro del juego.
- Selector de estación: mesa de crafteo, horno, alto horno, hoguera y ahumador.
- Editor de receta con grilla 3x3, estación, resultado y botón de opciones.
- Opciones editables: categoría, hidden, tipo SHAPED/SHAPELESS, tiempo de cocción, XP vanilla y XP de Forjador.
- Guardado automático en `plugins/MDVRecetas/recipes/editor.yml`.
- El editor detecta automáticamente VANILLA, MMOITEMS o ITEMSTACK serializado.

## Comandos

- `/mdvrecetas` abre la guía visual.
- `/mdvrecetas reload` recarga recetas.
- `/mdvrecetas debugitem` muestra información del item en mano.
- `/mdvrecetas serializehand <id>` guarda un item exacto en Base64.
- `/mdvrecetas editor` abre el editor admin.

## Permisos

- `mdvrecetas.use` permite abrir la guía.
- `mdvrecetas.admin` permite reload, debug, serialize y editor.

## Nota del editor 0.4.0

El editor 0.4.0 es una primera versión funcional. Sirve para crear recetas simples y guardarlas en YAML. Todavía no incluye edición de recetas existentes, reemplazo vanilla desde GUI ni nombre manual de ID; genera IDs automáticos.
