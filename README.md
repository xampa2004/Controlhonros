# Control de hornos

App Android para controlar los ciclos de cocción de varios hornos y avisar con una alarma cuando termina cada tirada.

## Estructura

- `v3/` — versión actual del proyecto Android (Gradle, Java, sin dependencias externas).
- `.github/workflows/build.yml` — compila el APK en los servidores de GitHub.

## Cómo obtener el APK

1. Pestaña **Actions** → workflow **Compilar APK** (se lanza al hacer push o a mano con *Run workflow*).
2. Cuando esté en verde, entra en la ejecución → **Artifacts** → `ControlHornos-apk`.
3. Dentro está `app-release.apk`, ya firmado, listo para instalar en el móvil.

El workflow firma con la clave guardada en los secretos del repo (`KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`). Sin esos secretos compila, pero firma con la clave de depuración.

> Guarda el fichero `controlhornos.jks` y su contraseña: las próximas versiones deben firmarse con esa misma clave para poder instalarse encima sin desinstalar.

También se puede compilar en local con Android Studio (abrir la carpeta `v3/`) o con `cd v3 && ./gradlew assembleDebug` usando JDK 17.

## Instalación en el móvil

1. Instalar el APK (permitir "orígenes desconocidos").
2. Abrir la app y pulsar **CONFIGURAR AVISOS DEL MÓVIL** hasta que desaparezca el aviso amarillo (alarmas exactas, notificaciones y batería).
3. En móviles OPPO/ColorOS, además: Ajustes → Aplicaciones → Control de hornos → Batería → permitir actividad en segundo plano e inicio automático.
