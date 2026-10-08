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

## Lectura numérica

El lector usa primero el árbol de accesibilidad de Android. Busca la etiqueta indicada y revisa los elementos accesibles próximos antes de ampliar la búsqueda a los siguientes elementos visibles. Reconoce formatos habituales como `1234,56`, `1.234,56`, `1,234.56` y números enteros.

Las variables se conservan localmente hasta que se limpian desde la aplicación. Las operaciones se realizan con `BigDecimal` para evitar errores de coma flotante.

## Rendimiento

La ejecución continúa siendo dirigida por eventos de Accessibility. Los pasos internos avanzan con una demora mínima de 40 ms; cuando una pantalla todavía no está lista, Secu espera el siguiente evento de cambio de contenido en lugar de usar polling lento.

## Build

Cada cambio integrado en `main` ejecuta automáticamente **Build debug APK**. El artefacto generado se llama `secu-debug-apk`.

## Próximos bloques

- múltiples secuencias nombradas y llamadas entre secuencias;
- perfiles de acceso protegidos;
- selector visual de campos/elementos;
- toque por coordenadas y gestos;
- condiciones por presencia de texto;
- loops y reanudación de una secuencia después de otra;
- grabador de acciones;
- OCR como fallback cuando Accessibility no exponga un elemento.
