# MDVRecetas 0.6.0

Motor de recetas custom para MDVCRAFT.

## Nuevo en 0.6.0

- Firma de crafteo con varias líneas en `config.yml`.
- Línea vacía superior para separar la firma del lore original.
- Modificadores por nivel de Forjador.
- Opción nueva en el editor: `Modificadores`.
- Las recetas con modificadores se guardan con:

```yaml
forjador:
  modifiers: true
```

- Los modificadores se eligen según el nivel de Forjador usando `forjador-modifiers.chances`.
- Los modificadores se clasifican en:
  - `bad`
  - `normal`
  - `good`
  - `very-good`
  - `blocked`
- `reliquia` y `simbionte` quedan bloqueados por defecto mediante `blocked-contains`.
- El item se muestra normal en la receta y el modificador se aplica al tomar/craftear el resultado.
- El resultado base de MMOItems se intenta construir sin tirar modifiers internos aleatorios de MMOItems.

## Firma

```yaml
signature:
  enabled: true
  lore-lines:
    - ''
    - '&a &7🔨 &l&aForjado por: &e%player%'
```

## Modificadores por Forjador

```yaml
forjador-modifiers:
  enabled: true
  level:
    placeholder: '%mmocore_profession_level_forjador%'

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
```

MDVRecetas mira los modificadores que acepta el template de MMOItems y solo elige entre los IDs que estén clasificados en `qualities`.

## Comandos

- `/mdvrecetas`
- `/mdvrecetas admin`
- `/mdvrecetas editor`
- `/mdvrecetas reload`
- `/mdvrecetas debugitem`
- `/mdvrecetas serializehand <id>`
