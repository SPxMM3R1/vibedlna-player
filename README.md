# VibeM3U

Reproductor M3U para Android TV inspirado en la experiencia de un set-top box:
abre directamente la señal, descarga la lista nuevamente en cada inicio y muestra
una barra compacta al cambiar de canal.

La versión 0.2.1 añade navegación configurable, caché persistente de logos,
confirmación de salida y programación actual desde la EPG XMLTV declarada por la M3U.

## Descargar

- [VibeM3U v0.2.1](https://gitlab.com/roberto.ramos.dz/streambox-tv/-/raw/main/releases/VibeM3U-v0.2.1.apk)
- [VibeM3U v0.2.0](https://gitlab.com/roberto.ramos.dz/streambox-tv/-/raw/main/releases/VibeM3U-v0.2.0.apk)
- [StreamBox TV v0.1.0](https://gitlab.com/roberto.ramos.dz/streambox-tv/-/raw/main/releases/StreamBoxTV-v0.1.0.apk)

## Instalar con Downloader

- VibeM3U v0.2.0: código **6326540** — [aftv.news/6326540](https://aftv.news/6326540)
- StreamBox TV v0.1.0: código **7068069** — [aftv.news/7068069](https://aftv.news/7068069)

En Downloader, escribe el código correspondiente y confirma la descarga.

## Controles

- `↑` / `↓`: canal anterior o siguiente; el sentido puede invertirse en Configuración.
- `OK`: mostrar la información del canal.
- Mantener `OK`, botón `Menú` o botón `Configuración`: editar la URL M3U.
- `Atrás`: confirmar salida o volver desde Configuración.

## Compilar

En Windows, con Android SDK y JDK configurados:

```powershell
.\gradlew.bat assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.
