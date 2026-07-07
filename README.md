# MDVRecetas 0.6.3

Motor de recetas custom para MDVCRAFT.

## Nuevo en 0.6.3

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


## MDVRecetas 0.6.3

Hotfix y placeholders:

- Corrige shift-click/click derecho en recetas con firma o modificadores.
  - Ahora MDVRecetas procesa cada item creado por separado.
  - Cada item recibe su propia firma y su propio roll de modificador.
- Añade placeholders de PlaceholderAPI para mostrar probabilidades actuales de Forjador.

Placeholders recomendados para la UI de MMOCore:

```yaml
- '&8Calidad de forja actual:'
- '&7Dañado: &c%mdvrecetas_forjador_chance_danado%% &8| &7Estable: &e%mdvrecetas_forjador_chance_estable%%'
- '&7Refinado: &a%mdvrecetas_forjador_chance_refinado%% &8| &7Magistral: &2%mdvrecetas_forjador_chance_magistral%%'
```

Placeholders disponibles:

```text
%mdvrecetas_forjador_nivel%
%mdvrecetas_forjador_chance_danado%
%mdvrecetas_forjador_chance_estable%
%mdvrecetas_forjador_chance_refinado%
%mdvrecetas_forjador_chance_magistral%
%mdvrecetas_forjador_chance_bad%
%mdvrecetas_forjador_chance_normal%
%mdvrecetas_forjador_chance_good%
%mdvrecetas_forjador_chance_very_good%
%mdvrecetas_forjador_probabilidades_1%
%mdvrecetas_forjador_probabilidades_2%
%mdvrecetas_forjador_probabilidades%
```

Nombres roleros usados:

```text
Dañado = bad
Estable = normal
Refinado = good
Magistral = very-good
```
