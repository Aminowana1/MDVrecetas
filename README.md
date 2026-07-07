# MDVRecetas 0.4.1

Motor de recetas custom para MDVCRAFT con guía visual tipo Terraria, buscador por ingrediente, hornos custom y editor in-game.

## Nuevo en 0.4.1

- `/mdvrecetas admin`: catálogo admin de recetas.
- El catálogo admin muestra recetas visibles y ocultas en sus categorías.
- Click izquierdo sobre una receta admin: abre el visualizador normal.
- Click derecho sobre una receta admin: abre el editor para modificar esa receta.
- El editor puede guardar cambios sobre una receta existente.
- Botón gris en edición: resetea la receta al estado original.
- Botón rojo en edición: elimina la receta del YAML.
- Opciones nuevas del editor:
  - Asignar ID manual por chat.
  - Reemplazar receta vanilla.
  - Asignar key vanilla por chat, por ejemplo `minecraft:golden_apple`.
- Si no asignas ID manual, el editor genera una ID automática como antes.
- No permite guardar una ID duplicada.

## Comandos

- `/mdvrecetas` abre la guía de recetas.
- `/mdvrecetas admin` abre el catálogo admin.
- `/mdvrecetas editor` abre el editor para crear una receta nueva.
- `/mdvrecetas reload` recarga recetas.
- `/mdvrecetas debugitem` muestra información del item en mano.
- `/mdvrecetas serializehand <id>` serializa el item en mano como ITEMSTACK.

## Notas

El editor guarda recetas en `plugins/MDVRecetas/recipes/editor.yml`.

Al editar una receta existente, MDVRecetas elimina la definición vieja de los YAML y guarda la nueva versión en `editor.yml`. Esto evita duplicados de ID.
