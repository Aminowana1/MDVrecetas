# MDVRecetas 0.6.21 — rendimiento al comprobar recetas

## Instalación

1. Guarda una copia del JAR anterior y de la carpeta de MDVRecetas.
2. Con el servidor detenido, sustituye el JAR anterior por MDVRecetas-0.6.21.jar. No dejes ambas versiones en plugins.
3. Conserva tu configuración y tus archivos de recetas: no hay opciones nuevas obligatorias ni cambios de formato.
4. Inicia el servidor y comprueba el cargamento de madera y tus recetas personalizadas, con clic normal y Shift + clic.

No hace falta cambiar los ingredientes a `MMO_ID`. Se mantiene `SIMILAR` como valor predeterminado para MMOItems.

## Corrección

Antes se construían objetos completos de MMOItems al comparar ingredientes de recetas que ni siquiera correspondían al objeto colocado. Por ejemplo, los troncos del cargamento de madera podían provocar construcciones de ingredientes MMOItems de otras recetas.

- Las recetas candidatas se filtran por cantidad de casillas ocupadas y, para recetas con forma, por posición.
- La identidad de MMOItems se comprueba antes de construir una referencia para las comparaciones estrictas.
- Las referencias de ingredientes se reutilizan en una caché de hasta 1024 entradas, con renovación al usarlas después de 60 segundos. No hay una tarea periódica reconstruyéndolas todas.
- Recargar recetas, guardar desde el editor o eliminar recetas invalida las referencias correspondientes mediante el vaciado de la caché. El índice de patrones se reconstruye cuando cambia el registro.
- La lectura de identidad reutiliza los métodos de la API descubiertos al iniciar el plugin.
- Las recetas sin forma usan una asignación acotada de ingredientes, evitando búsquedas combinatorias. Una receta de nueve casillas requiere como máximo 81 comparaciones de compatibilidad.
- Se elimina una construcción adicional del resultado que solo se utilizaba para calcular su cantidad en el evento de crafteo.

Los resultados de la mesa y los objetos entregados siguen usando la ruta de generación original, sin reutilizar la caché de ingredientes. Se conservan las comprobaciones estrictas de metadatos de `SIMILAR` y `EXACT`; no se convierten a comparaciones únicamente por identificador.

Si modificas plantillas en MMOItems, recarga primero MMOItems y después ejecuta `/mdvrecetas reload` para descartar inmediatamente las referencias anteriores. Las plantillas de ingredientes con propiedades aleatorias usan una referencia estable durante el período de caché, en lugar de generar una nueva en cada comparación.

## Verificación y límites

Compilación con Java 21 y 16 pruebas automatizadas: patrones, cuadrícula de 2×2, cantidades, prioridad de recetas, ingredientes incompatibles, asignaciones sin forma, identidad MMOItems, comparación estricta, caducidad, invalidación y límite de caché.

En una prueba controlada de 9000 comparaciones estrictas del mismo ingrediente se solicita una sola construcción de referencia MMOItems; la ruta anterior solicitaba una por comparación. La prueba usa un sustituto de MMOItems para contar llamadas: no es una medición de TPS ni una prueba con miles de jugadores reales.

No se ha realizado una prueba de integración en tu servidor con tus versiones de MMOItems y MythicLib. El parche corrige el trabajo repetido identificado en la comprobación de recetas; generar los resultados reales todavía tiene un coste. Tras instalarlo, comprueba el mismo crafteo masivo y compara un nuevo perfil de rendimiento antes de dar por resueltos todos los tirones.
