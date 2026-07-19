# MDVRecetas 0.6.12

## Permiso de creador separado

Se añade `mdvrecetas.editor` para abrir `/mdvrecetas editor` y crear recetas nuevas sin conceder `mdvrecetas.admin`.

- `mdvrecetas.editor`: crea recetas nuevas.
- `mdvrecetas.admin`: conserva catálogo administrativo, edición de recetas existentes, recarga, debug y herramientas técnicas.
- `mdvrecetas.admin` hereda `mdvrecetas.editor`.
- El editor bloquea IDs que ya existen, por lo que un creador no puede sobrescribir una receta publicada.
- `openEditRecipe` valida explícitamente `mdvrecetas.admin`, incluso si otro plugin intenta abrirlo por API.

Permisos recomendados para un administrador de contenido:

```text
mdvrecetas.editor true
mdvrecetas.admin false
```
