# MDVRecetas 0.6.13

## Optimización de hologramas de experiencia

Se corrigió la creación masiva de hologramas cuando un jugador fabrica muchos
objetos que otorgan poca experiencia de Forjador.

### Nuevo comportamiento

- Dentro del radio configurado existe como máximo un holograma de XP de
  MDVRecetas.
- Un nuevo premio cercano reutiliza el `TextDisplay` ya existente.
- La experiencia se acumula visualmente: varios premios de `1 XP` pueden verse
  como un único `+64 EXP!`.
- Cada premio renueva el tiempo de desaparición del holograma.
- Los duplicados etiquetados dentro del radio se eliminan automáticamente.
- Al apagar o recargar el plugin se eliminan sus hologramas activos.
- No se eliminan hologramas de otros plugins.

### Configuración

```yaml
forjador:
  hologram:
    single-per-radius: true
    merge-radius: 8.0
    accumulate-xp: true
```

Las claves son retrocompatibles. Si no existen en una configuración antigua,
se utilizan automáticamente los valores mostrados arriba.
