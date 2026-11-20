# Trabajar en CheapGasofaBot

El código de producción está en `src/main/java/gasofa/bot` y las pruebas en el paquete equivalente de `src/test/java`. La [guía de ejecución](EJECUCION.md) describe cómo seleccionar Java 27 y usar el wrapper.

- Para cambiar los criterios de búsqueda, modifica `Catalogo` y añade a `CatalogoTest` un caso con precios y coordenadas que reproduzcan el comportamiento esperado.
- Para cambiar comandos o botones, comprueba en `CheapGasofaBotTest` qué mensaje se envía o se edita y qué estación resuelve cada pulsación. El cliente simulado permite hacerlo sin una cuenta de Telegram.
- Antes de proponer un cambio, ejecuta `.\mvnw.cmd clean verify` en Windows o `sh mvnw clean verify` en Linux y macOS. Actualiza la ayuda del bot y el README si cambia el diálogo.
- Describe en la propuesta el problema, el comportamiento observable y las pruebas realizadas. Para ilustrar incidencias, utiliza ubicaciones de ejemplo y omite tokens y datos personales.
