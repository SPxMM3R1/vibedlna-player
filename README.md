# VibeM3U

Reproductor M3U para Android TV inspirado en la experiencia de un set-top box:
abre directamente la señal, descarga la lista nuevamente en cada inicio y muestra
una barra compacta al cambiar de canal.

La versión 0.2 añade navegación de canales configurable y una caché persistente
para que los logos ya descargados aparezcan sin volver a consultar Internet.

## Descargar

- [VibeM3U v0.2.0](https://gitlab.com/roberto.ramos.dz/streambox-tv/-/raw/main/releases/VibeM3U-v0.2.0.apk)
- [StreamBox TV v0.1.0](https://gitlab.com/roberto.ramos.dz/streambox-tv/-/raw/main/releases/StreamBoxTV-v0.1.0.apk)

## Instalar con Downloader

El código **7068069** continúa descargando la versión 0.1.0. La versión 0.2.0
tendrá su propio código para conservar ambas descargas disponibles.

Enlace de la v0.1.0: [aftv.news/7068069](https://aftv.news/7068069).

## Controles

- `↑` / `↓`: canal anterior o siguiente; el sentido puede invertirse en Configuración.
- `OK`: mostrar la información del canal.
- Mantener `OK`, botón `Menú` o botón `Configuración`: editar la URL M3U.
- `Atrás`: salir o volver desde Configuración.

## Compilar

En Windows, con Android SDK y JDK configurados:

```powershell
.\gradlew.bat assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.
