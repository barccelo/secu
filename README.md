# Secu

## Estado actual

Versión **0.4.0**.

### Selección de campos

- **Pulsar campo**: localiza un campo editable por texto, `hintText`, `contentDescription`, `viewId`, relación de etiqueta o proximidad visual.
- **Escribir en campo**: sintaxis `Campo -> texto`. Localiza el campo indicado y escribe directamente en él.
- Si un campo no responde a `ACTION_CLICK`, Secu usa como fallback un toque en el centro de sus límites accesibles.

### Toques por coordenadas

- **Pulsar coordenada**: ejecuta un toque real mediante `dispatchGesture`. Sintaxis manual: `x,y`.
- El botón **Seleccionar punto en pantalla** minimiza Secu, vuelve a la app anterior y activa una capa de Accessibility.
- El siguiente toque se captura como coordenada. Al volver a Secu, el valor queda cargado automáticamente y el tipo de paso cambia a **Pulsar coordenada**.

### Firma y actualizaciones

GitHub Actions usa la firma persistente configurada en `SECU_SIGNING_BUNDLE_BASE64` y `versionCode` automático mediante `github.run_number`.
