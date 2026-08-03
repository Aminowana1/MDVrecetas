# MDVRecetas 0.6.15

## Ranuras de gemas por nivel de Forjador

- Mantiene intacta la detección actual de modificadores de calidad desde la plantilla de MMOItems.
- Añade una segunda tirada independiente para ranuras de gemas.
- Detecta automáticamente grupos con formato:
  - `mdv_sockets_fisica_t2`
  - `mdv_sockets_distancia_t3`
  - `mdv_sockets_arcana_t4`
  - `mdv_sockets_soporte_t5`
- No usa `tier: COMUN/ESPECIAL/...` para identificar progresión; el tier se toma del ID del grupo.
- Aplica en una sola construcción del MMOItem:
  1. modificador de calidad;
  2. modificador de ranuras.
- La tirada de ranuras depende del nivel de Forjador e interpola linealmente entre nivel 1 y 50.
- Nivel 1 conserva las probabilidades base de drops de MMOItems/MythicMobs.
- Guarda en PDC el grupo, modificador y cantidad de ranuras elegidos.
- Restaura esos metadatos después de Revision ID.
- Los objetos T1 quedan sin ranuras porque no poseen grupos `mdv_sockets_*_t1`.

## Configuración

Se añadió `forjador-sockets` a `modifiers.yml`. En instalaciones existentes, puede copiarse el bloque del archivo incluido en el código fuente. Si el bloque falta, el plugin utiliza los mismos valores como defaults internos.
