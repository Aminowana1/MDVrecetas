# MDVRecetas 0.6.2

Motor de recetas custom para MDVCRAFT.

## Nuevo en 0.6.2

- Hotfix visual de modificadores: si el modifier tiene prefix, MDVRecetas lo añade al nombre del item crafteado.
  - Ejemplo: `&8Oxidado` + `&aCoraza de Soldado` → `&8Oxidado &aCoraza de Soldado`.
- La tabla de modificadores por Forjador ahora vive en `plugins/MDVRecetas/modifiers.yml`.
- `config.yml` queda más limpio.
- Compatibilidad con config vieja: si una ruta no existe en `modifiers.yml`, MDVRecetas intenta leerla desde `config.yml`.
- Se añadió `prefix-overrides` en `modifiers.yml` como respaldo si el prefix no se puede leer automáticamente desde MMOItems.

## Firma

```yaml
signature:
  enabled: true
  lore-lines:
    - ''
    - '&a &7🔨 &l&aForjado por: &e%player%'
```

## Modificadores por Forjador

Ahora se configuran en:

```text
plugins/MDVRecetas/modifiers.yml
```

Ejemplo:

```yaml
forjador-modifiers:
  enabled: true

  chances:
    level-1:
      bad: 40
      normal: 50
      good: 9
      very-good: 1
    level-50:
      bad: 5
      normal: 45
      good: 35
      very-good: 15

  qualities:
    bad:
      - t1_tanque_oxidado
    normal:
      - t1_normal
    good:
      - t1_tanque_pulido
    very-good:
      - t1_tanque_reforzado
    blocked:
      - reliquia
      - simbionte
```

Si un prefix no sale automáticamente, puedes forzarlo así:

```yaml
forjador-modifiers:
  prefix-overrides:
    t1_tanque_oxidado: '&8Oxidado'
```

## Recetas con modifiers

```yaml
forjador:
  exp: 2.0
  signature: true
  modifiers: true
```

## Comandos

- `/mdvrecetas`
- `/mdvrecetas admin`
- `/mdvrecetas editor`
- `/mdvrecetas reload`
- `/mdvrecetas debugitem`
- `/mdvrecetas serializehand <id>`
