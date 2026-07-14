# MDVRecetas 0.6.9

- Añade un submenú 3x3 en Opciones para configurar la comparación de cada ingrediente.
- Los ingredientes MMOItems conservan por defecto el comportamiento anterior (comparación estándar/SIMILAR).
- Al hacer click sobre una casilla MMOItems se alterna entre ESTÁNDAR y MMO_ID.
- MMO_ID compara solo TYPE + ID e ignora firma, durabilidad, modificadores, prefijos y runas.
- Casillas vacías y objetos que no son MMOItems no cambian.
- Las recetas existentes no se borran ni se migran; sus valores match explícitos se conservan al editarlas.
