# MDVRecetas 0.5.0

Motor de recetas custom para MDVCRAFT.

## Nuevo en 0.5.0

- Firma de crafteo opcional por receta.
- Lore configurable: `&l&aForjado por: &e%player%`.
- Datos internos guardados en PersistentDataContainer:
  - UUID del creador.
  - Nombre del creador.
  - ID de receta usada.
- Nueva opción en el editor para activar/desactivar firma.
- Por defecto todas las recetas quedan sin firma.

## YAML

```yaml
forjador:
  exp: 2.0
  signature: true
  modifiers: false
```

## Editor

Usa:

```text
/mdvrecetas editor
```

En Opciones de receta puedes activar **Firma de crafteo**.

## Config

```yaml
signature:
  enabled: true
  lore-line: '&l&aForjado por: &e%player%'
```

## Notas

La firma solo se aplica al resultado final de recetas donde `forjador.signature` esté en `true`.
No se añade fecha visible por ahora.
