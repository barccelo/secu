# Secu

Secu es una aplicación Android para crear y ejecutar automatizaciones sobre la interfaz del teléfono.

## Estado actual

Versión **0.2.0**. El núcleo de accesibilidad ya fue validado en un Samsung Galaxy S25 Ultra con Android 16.

Secu ahora permite construir una secuencia local de pasos, reordenarla, eliminar pasos y ejecutarla de principio a fin mediante `AccessibilityService`.

### Pasos disponibles

- **Esperar texto**: pausa hasta que aparezca un texto o descripción accesible.
- **Pulsar texto**: busca el texto y pulsa el nodo accesible correspondiente.
- **Esperar tiempo**: espera una cantidad de milisegundos.
- **Volver atrás**: ejecuta la acción global Atrás.
- **Escribir texto**: escribe en el campo editable enfocado; si no hay uno enfocado, usa el primer campo editable disponible.
- **Abrir app**: lanza una aplicación por su nombre de paquete, por ejemplo `com.google.android.calculator`.

La secuencia y el registro se guardan localmente en el dispositivo.

## Configuración

- Android 16 / API 36 como objetivo actual
- `compileSdk = 36`
- `targetSdk = 36`
- `minSdk = 26`
- JDK 17
- package: `com.barccelo.secu`

## Uso

1. Instala Secu.
2. Activa **Secu · Automatización** en los ajustes de accesibilidad.
3. Añade pasos en la pantalla principal.
4. Usa ↑ y ↓ para cambiar el orden.
5. Pulsa **Ejecutar secuencia**.
6. Revisa **Registro** para ver qué hizo cada paso.

## Build

Cada cambio integrado en `main` ejecuta el workflow **Build debug APK**. El artefacto generado se llama `secu-debug-apk`.

## Próximos bloques

- selector visual de aplicaciones instaladas para no escribir paquetes manualmente;
- toque por coordenadas y gestos;
- condiciones y ramas;
- variables/tokens;
- grabador de acciones;
- captura de pantalla + OCR como fallback cuando Accessibility no exponga un elemento.
