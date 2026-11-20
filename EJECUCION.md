# Ejecutar CheapGasofaBot

Ejecuta los comandos desde la raíz del proyecto, donde está `pom.xml`. Necesitas un JDK 27. El wrapper descarga Maven y sus dependencias en la primera compilación. Para atender mensajes, el proceso necesita acceso a Telegram y al servicio de precios del Ministerio, además de permiso de escritura en el directorio de trabajo.

## 1. Seleccionar Java y compilar

**Windows (PowerShell).** Si usas Java 25 para otro proyecto, abre una ventana nueva y configura Java 27 solo para esta sesión. Sustituye la ruta de ejemplo por la de tu JDK 27.

```powershell
$env:JAVA_HOME = 'C:\ruta\al\jdk-27'
& "$env:JAVA_HOME\bin\java.exe" -version
.\mvnw.cmd clean verify
```

**Linux o macOS.** Ajusta la ruta al directorio del JDK que contiene `bin/java`.

```sh
export JAVA_HOME=/ruta/al/jdk-27
"$JAVA_HOME/bin/java" -version
sh mvnw clean verify
```

Comprueba que la versión es 27 y que Maven termina con `BUILD SUCCESS`. El archivo ejecutable con dependencias es `target/cheapgasofabot.jar`. Las pruebas usan un catálogo local y simulan Telegram, por lo que puedes compilarlas y ejecutarlas antes de configurar el bot.

## 2. Configurar el token

Si ya tienes `.telegram-token` en esta carpeta, puedes pasar al arranque. Para una instalación nueva, guarda el token facilitado por BotFather en ese archivo de texto UTF-8, en una sola línea. También puedes inyectar la variable de entorno `TELEGRAM_BOT_TOKEN` desde la configuración de tu servicio. La variable tiene prioridad cuando contiene un valor.

## 3. Arrancar, comprobar y detener

**Windows**

```powershell
& "$env:JAVA_HOME\bin\java.exe" -jar .\target\cheapgasofabot.jar
```

**Linux o macOS**

```sh
"$JAVA_HOME/bin/java" -jar target/cheapgasofabot.jar
```

Cuando aparezca `Bot iniciado`, abre el chat y envía `/help`. Para comprobar la búsqueda, comparte una ubicación o escribe una localidad. Cambia el carburante en la primera fila y pulsa «Obtener ubicación» en la estación que elijas. En un primer arranque puede haber una breve espera hasta que se descargue el catálogo.

Usa una sola instancia por token. Para detener una ejecución en primer plano, pulsa **Ctrl+C**. Detén la instancia antes de sustituir el JAR y vuelve a ejecutar el comando de arranque.

## Incidencias frecuentes

| Síntoma | Comprobación |
| --- | --- |
| Java indica que la versión del archivo de clase es incompatible | Ejecuta el JAR con el mismo JDK 27 que usaste para compilar. |
| El bot pide el token aunque el archivo existe | Comprueba el directorio desde el que has arrancado el proceso. El archivo se resuelve respecto a ese directorio. |
| El catálogo no está disponible | Revisa el acceso al Ministerio y los permisos de escritura para `descarga.json`. Tras un fallo, el proceso reintenta la actualización y puede servir la última copia válida. |
| Telegram indica un conflicto al recibir actualizaciones | Comprueba si otra instancia está utilizando el mismo token. |
