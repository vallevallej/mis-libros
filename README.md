# Mis libros — Android v1.0 (prototipo inicial)

Aplicación Android nativa para abrir documentos y leerlos usando el motor Text-to-Speech configurado en Android.

## Funciones incluidas
- Importación mediante selector de archivos Android.
- Extracción de texto de TXT, EPUB (contenido HTML) y PDF con texto seleccionable.
- Reproducción TTS, detener y vista previa del texto.
- Acceso a los ajustes de voz del sistema.

## Voz neuronal en español
Esta primera base usa la API estándar Android TextToSpeech. Para obtener voz neuronal local, instala un motor TTS compatible, por ejemplo HayaiTTS (https://github.com/HayaiApp/HayaiTTS), descarga una voz es-ES y selecciónalo como motor preferido en Ajustes de Android > Texto a voz. La disponibilidad y calidad dependen de la voz instalada.

## Limitaciones de esta base
- El control de velocidad está dibujado en la interfaz; la integración de velocidad efectiva con el motor seleccionado queda pendiente.
- La reproducción larga en segundo plano/pantalla apagada, memoria de posición, biblioteca persistente, OCR de PDF escaneados y orden de lectura EPUB por su spine quedan para las siguientes iteraciones.
- Es un prototipo inicial y necesita compilación y pruebas en un dispositivo Android real antes de considerarse listo para instalar.

## Generar APK
El workflow `.github/workflows/build-apk.yml` compila el APK debug en GitHub Actions. Sube el contenido de este proyecto a un repositorio GitHub y ejecuta **Actions > Build Mis libros APK > Run workflow**. Descarga el artefacto `Mis-libros-debug-apk` cuando termine.

También puedes abrir la carpeta en Android Studio y ejecutar `Build > Build Bundle(s) / APK(s) > Build APK(s)`.
