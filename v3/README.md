# Control de hornos — v3

App Android nativa (Java, sin librerías externas) para controlar varios hornos: cada horno tiene su ciclo en minutos y cada tirada programada suena con una alarma al terminar, aunque la app esté cerrada.

## Compilar

- Android Studio: abrir esta carpeta y *Build → Build APK(s)*.
- Línea de comandos: `./gradlew assembleDebug` (JDK 17, Android SDK con compileSdk 35).
- GitHub Actions: el workflow de la raíz del repo compila el APK y lo publica como artifact `ControlHornos-apk`.

## Requisitos de Android

- Android 8.0 (API 26) o superior.
- Permisos que la app pide desde el botón **CONFIGURAR AVISOS DEL MÓVIL**:
  - alarmas y recordatorios exactos,
  - notificaciones,
  - exención de optimización de batería.
- En OPPO/ColorOS: Ajustes → Aplicaciones → Control de hornos → Batería → permitir actividad en segundo plano e inicio automático. Sin esto el sistema puede matar la app y no sonar la alarma.

## Cómo funciona

- `MainActivity` — interfaz completa creada por código, refresco cada segundo.
- `Db` — SQLite: hornos (`ovens`, con temperatura, curva y formato) y tiradas (`pieces`, con la foto del ciclo al crearse y marca de historial).
- `TimelineView` — línea de tiempo con todas las tiradas activas sobre un mismo eje de horas, una fila por horno.
- `AlarmUtil` — programa la alarma con `setAlarmClock` (la API más fiable) y cae a `setExactAndAllowWhileIdle` si falla.
- `AlarmReceiver` — muestra la notificación de fin de ciclo con sonido de alarma.
- `BootReceiver` — reprograma las alarmas pendientes al reiniciar el móvil o actualizar la app.

## Uso

- **+ TIRAR** guarda la tirada en el acto (entrada = ahora, salida = ahora + ciclo) y programa el aviso. No pregunta nada; los detalles se editan después con **EDITAR**.
- **YA EN MARCHA** es para cuando se olvidó pulsar TIRAR: se indica cuánto le queda y la entrada se calcula hacia atrás.
- Pulsando la barra de **temperatura, curva y formato** del horno se definen esos datos; cada tirada guarda una copia al crearse.
- **RECOGIDA** manda la tirada al **HISTORIAL**, conservando duración del ciclo, temperatura, curva y formato.
