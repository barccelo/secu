# Secu

Secu es una aplicación Android para crear y ejecutar automatizaciones sobre la interfaz del teléfono.

## Estado actual

Prototipo **0.1.0**. Esta primera base valida el núcleo de automatización mediante AccessibilityService.

Actualmente puede:

- registrar un servicio de accesibilidad;
- detectar cambios de aplicación/ventana;
- leer el árbol accesible de la pantalla activa;
- buscar un elemento por texto o descripción;
- localizar un ancestro pulsable;
- ejecutar ACTION_CLICK;
- mantener un pequeño registro local de diagnóstico;
- dejar una acción armada mientras se cambia manualmente a otra aplicación.

Todavía no incluye OCR, captura de pantalla, grabador de secuencias ni editor completo de automatizaciones.

## Configuración

- Kotlin
- Android Gradle Plugin 9.4.0
- compileSdk = 36
- targetSdk = 36
- minSdk = 26
- JDK 17
- package: com.barccelo.secu

## Primera prueba

1. Instala y abre Secu.
2. Pulsa **Abrir ajustes de accesibilidad**.
3. Activa **Secu · Automatización**.
4. Regresa a Secu.
5. Escribe el texto de un botón visible en otra app, por ejemplo Aceptar.
6. Pulsa **Armar: buscar y pulsar**.
7. Abre manualmente la aplicación objetivo.
8. Cuando Secu encuentre el texto, intentará pulsar el elemento automáticamente.
9. Regresa a Secu y revisa **Registro**.

## Próximos pasos

1. Inspector completo del árbol de accesibilidad.
2. Motor de pasos: esperar, pulsar, escribir, volver, swipe y pausa.
3. Apertura de aplicaciones como parte de una secuencia.
4. Condiciones y variables/tokens.
5. Grabador de acciones.
6. Captura de pantalla + OCR como fallback para elementos no accesibles.
7. Ejecutor con historial y diagnóstico por paso.

Secu solo ejecutará automatizaciones que el usuario configure y active en su propio dispositivo.
