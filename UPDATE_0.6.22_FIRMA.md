# MDVRecetas 0.6.22 — Firma única al reparar

Esta versión corrige la duplicación de la firma al aplicar un consumible de reparación de MMOItems sobre un arma fabricada. Aunque repares varias veces, el lore conserva un solo bloque de firma.

Si un objeto ya tiene varias copias de la firma, se consolidan durante la siguiente reparación que pase por la integración de MMOItems. La misma corrección se utiliza al aplicar o retirar gemas y al actualizar Revision ID.

## Instalar

1. Apaga el servidor.
2. Sustituye el jar anterior de MDVRecetas por `MDVRecetas-0.6.22.jar`.
3. Conserva la carpeta de datos, tus recetas, `config.yml` y `modifiers.yml`.
4. Vuelve a iniciar el servidor.

No necesitas añadir ni cambiar opciones de configuración. Se sigue usando tu bloque `signature:` actual, tanto `lore-lines` como la opción antigua `lore-line`.

## Qué corrige

La versión anterior buscaba una coincidencia exacta del texto con sus códigos de color. Bukkit y MMOItems pueden cambiar esos códigos al actualizar el objeto, aunque visualmente la firma siga siendo la misma. MDVRecetas interpretaba que faltaba y añadía otra.

Ahora se compara el texto visible del bloque completo de firma, se reconoce aunque cambie el formato de color y se conserva una sola copia. Se mantienen el creador original, la receta y las líneas ajenas a la firma. No se elimina todo el lore ni se reconstruye el arma desde su plantilla: la durabilidad reparada, las mejoras y las gemas siguen procediendo del objeto que devuelve MMOItems.

La limpieza solo afecta a firmas identificadas por los datos de MDVRecetas y el formato configurado. No busca ni borra indiscriminadamente líneas repetidas de otros plugins. No realiza escaneos por tick.

Fuentes oficiales que explican el cambio de formato y la aplicación del consumible: [LoreUpdate de MMOItems](https://gitlab.com/phoenix-dvpmt/mmoitems/-/raw/master/MMOItems-API/src/main/java/net/Indyuce/mmoitems/api/item/util/LoreUpdate.java), [LoreBuilder de MMOItems](https://gitlab.com/phoenix-dvpmt/mmoitems/-/raw/master/MMOItems-API/src/main/java/net/Indyuce/mmoitems/api/item/build/LoreBuilder.java), [ItemUse de MMOItems](https://gitlab.com/phoenix-dvpmt/mmoitems/-/raw/master/MMOItems-API/src/main/java/net/Indyuce/mmoitems/listener/ItemUse.java).

## Comprobar en el servidor

- Fabrica un arma, daña su durabilidad y aplica cinco reparaciones. Debe conservar una sola firma y aumentar la durabilidad como antes.
- Repite con un arma que ya tenga varias firmas: la siguiente reparación debe dejar una sola.
- Repara el arma con otro jugador: debe seguir mostrando al forjador original.
- Comprueba que el resto del lore, el nombre, las estadísticas y las gemas siguen presentes.

Compilado con Maven (`clean package`): BUILD SUCCESS y 37 pruebas aprobadas. Incluyen 21 pruebas de firma y del puente de consumibles de MMOItems, además de las 16 pruebas existentes de recetas y rendimiento. La comprobación con los consumibles y las versiones instaladas en tu servidor sigue siendo necesaria.
