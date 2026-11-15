# VibeDLNA Player

[![Android CI](https://github.com/SPxMM3R1/vibedlna-player/actions/workflows/android-ci.yml/badge.svg)](https://github.com/SPxMM3R1/vibedlna-player/actions/workflows/android-ci.yml)

Reproductor DLNA para Android TV con la interfaz compacta de VibeM3U.

## Funcionamiento

- Descubre automáticamente los MediaServers DLNA/UPnP anunciados en la red local.
- Permite elegir un servidor y navegar sus carpetas virtuales mediante
  `ContentDirectory:Browse` ejecutado por jUPnP.
- Guarda en almacenamiento privado las miniaturas que el servidor anuncia y
  deja el estado vacío cuando el servidor no proporciona una imagen lista.
- Muestra las carpetas en tarjetas compactas y los videos en tarjetas 16:9.
- Conserva temporalmente la posición de un video durante una hora para
  reanudarlo automáticamente; no mantiene historial permanente.

## Controles

Servidores:

- Flechas y `OK`: elegir un servidor. Si el guardado es el único en línea,
  el Player entra directo a su biblioteca.
- `Atrás`: volver a la biblioteca si ya hay un servidor; si no, salir.

Biblioteca:

- Izquierda/derecha y arriba/abajo: recorrer la columna de carpetas y la
  grilla de videos.
- `OK` en una carpeta: mostrar sus videos; si tiene subcarpetas, la columna
  pasa a mostrarlas, con la carpeta abierta arriba, y aparece `‹` para subir un
  nivel. La biblioteca recuerda la ruta completa y la vuelve a abrir al entrar.
- Si el servidor no entrega todo el contenido tras reintentar, la grilla lo
  muestra con el aviso «No se pudo leer todo: faltan algunos elementos».
- `OK` en un video: si tiene avance guardado, abre Detalle (reanudar o desde
  el inicio); si no, reproduce desde el inicio.
- `Atrás`: subir un nivel de carpetas o confirmar la salida en la raíz.

Detalle:

- `OK` en «Reanudar» o «Desde el inicio»; «Volver a la carpeta» regresa.

Reproductor:

- Izquierda/derecha: retroceder o adelantar 10 s (más rápido al mantener).
- `OK`: pausar o reanudar.
- Arriba (`▲`): elegir audio y subtítulos. Izquierda/derecha cambian de
  grupo, arriba/abajo de pista, `OK` elige y `Atrás` cierra.
- `Atrás`: ocultar primero la interfaz y, al pulsarlo otra vez, cerrar el video.
- La interfaz se oculta sola a los 4,5 s mientras el video se reproduce; en
  pausa se queda. Al terminar, pasa al siguiente video de la carpeta; si no hay
  otro, vuelve a la biblioteca con el foco en ese video.

Opciones (`Menú` o `Configuración`, en cualquier pantalla):

- Pestañas Biblioteca, Reproducción, Servidores y Acerca de; izquierda/derecha
  entre pestañas, `OK` cambia el valor de una fila o ejecuta su acción.
- Biblioteca: orden de las carpetas, ocultar archivos que la TV no puede abrir,
  y borrar las miniaturas guardadas.
- Reproducción: cuánto se recuerda dónde quedaste (1 hora, 1 día o siempre).
- Servidores: abrir en el último servidor o elegir cada vez; cambiar de servidor.
- Acerca de: versión y búsqueda de actualización.

## Actualizaciones

La app consulta la última Release pública de GitHub al abrirse. Si existe una
versión superior, descarga el APK, comprueba el paquete y la firma y solicita
la confirmación de Android para instalarlo.

GitHub Actions ejecuta las pruebas, Android Lint y la compilación. Las etiquetas
`vX.Y.Z` publican automáticamente una APK firmada en Releases.
