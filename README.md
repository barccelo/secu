# Secu

Secu es una aplicación Android para crear y ejecutar automatizaciones sobre la interfaz del teléfono.

## Estado actual

Versión **0.3.0**. Además del motor de secuencias, Secu incorpora variables numéricas, lectura de valores accesibles en pantalla, fórmulas, comparaciones y escritura de resultados.

### Pasos disponibles

- **Esperar texto**: espera un texto o descripción accesible.
- **Pulsar texto**: busca y pulsa un elemento.
- **Esperar tiempo**: espera milisegundos.
- **Volver atrás**.
- **Escribir texto**: escribe en el campo editable activo.
- **Abrir app**: abre una aplicación por package name.
- **Leer número**: lee un número próximo a una etiqueta y lo guarda. Sintaxis: `Etiqueta -> variable`.
- **Calcular**: evalúa una fórmula y guarda el resultado. Sintaxis: `resultado = min(a, b)`. Admite `+`, `-`, `*`, `/`, paréntesis, `min()` y `max()`.
- **IF numérico**: compara expresiones y aplica un salto adicional. Sintaxis: `a >= b ? +0 : +1`. Admite `>`, `<`, `>=`, `<=`, `==`, `!=`.
- **Escribir variable**: escribe el valor de una variable en el campo editable activo.

## Firma persistente y actualizaciones

Los APK de GitHub Actions deben usar siempre la misma clave de firma. El workflow ahora exige el secret de repositorio `SECU_SIGNING_BUNDLE_BASE64`; si falta, el build se detiene antes de generar un APK instalable para evitar producir una versión con una firma distinta.

El bundle de firma contiene `secu-upload.jks` y `signing.properties`. Ese material no debe subirse al repositorio.

El `versionCode` se toma automáticamente de `github.run_number`, por lo que cada build de Actions tiene un código de versión creciente sin editar Gradle manualmente.

Las compilaciones anteriores a esta configuración usaban claves debug temporales de runners distintos. Por ello, la primera instalación que adopte la nueva firma persistente requiere reemplazar la versión antigua una sola vez. Después, los APK futuros con la misma firma se instalan como actualización.

## Build

Cada cambio integrado en `main` ejecuta automáticamente **Build debug APK**. El artefacto generado se llama `secu-debug-apk`.
