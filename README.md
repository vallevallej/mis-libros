# Mis libros — Android v1.1 (reproductor en segundo plano)

Aplicación Android nativa para abrir documentos y escucharlos con el motor Text-to-Speech seleccionado en Android.

## Incluido
- Importación de PDF con texto seleccionable, EPUB y TXT.
- Extracción de texto y vista previa.
- Reproducción TTS dividida en fragmentos para textos largos.
- Servicio Android en primer plano para mantener la lectura al salir de la aplicación o apagar la pantalla.
- Notificación con controles de pausar, continuar y detener.
- Control de velocidad aplicado al motor TTS.
- Acceso a los ajustes de voz de Android.
- Workflow de GitHub Actions para generar un APK debug.

## Sobre la voz neuronal
Esta versión **todavía no incluye un modelo neuronal propio**. Utiliza el motor TTS que el usuario tenga seleccionado en Android. Si se instala un motor compatible con voz neuronal española y se elige en los ajustes de Texto a voz, la aplicación lo utilizará a través de la API estándar de Android. La calidad, disponibilidad offline y compatibilidad dependen de ese motor y de la voz instalada.

## Limitaciones pendientes
- No se incluye todavía un motor neuronal empaquetado dentro de la aplicación; se utiliza el motor TTS seleccionado en Android.
- No hay biblioteca persistente ni guardado de posición de lectura.
- EPUB se extrae por nombre de archivo, no siguiendo todavía el orden de lectura declarado en el `spine`.
- Los PDF escaneados sin capa de texto necesitan OCR, aún no integrado.
- La pausa/continuación del servicio reanuda desde el fragmento actual, por lo que puede repetir unas palabras del fragmento.
- Hay que probar el comportamiento en un dispositivo Android real, especialmente con las restricciones de batería del fabricante.

## Generar APK desde GitHub
1. Abre la pestaña **Actions** del repositorio.
2. Selecciona **Build Mis libros APK**.
3. Pulsa **Run workflow** (o espera a que termine el flujo automático tras un `push`).
4. Abre la ejecución terminada y descarga el artefacto **Mis-libros-debug-apk**.
5. Dentro encontrarás `app-debug.apk`.

También puedes abrir el proyecto en Android Studio y ejecutar `Build > Build Bundle(s) / APK(s) > Build APK(s)`.

El APK debug sirve para instalar y probar; no es todavía una versión firmada de distribución.
