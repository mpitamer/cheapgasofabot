# CheapGasofaBot

Bot de Telegram para comparar precios de carburante en España. Envía una ubicación, un código postal de cinco cifras o el nombre de una localidad o municipio. Recibirás un listado con hasta tres estaciones, sus precios para el carburante elegido, dirección, horario publicado y fecha del catálogo. La primera fila de botones permite cambiar de combustible con un toque. El bot confirma la selección y recalcula el mismo listado. Pulsa el botón numerado «Obtener ubicación» para recibir el mapa de esa estación.

**Autor:** Moisés Pita Merino.

## Ejecución y desarrollo

La [guía de ejecución](EJECUCION.md) explica cómo compilar, configurar el token y arrancar el JAR con Java 27 en Windows, Linux y macOS. Para trabajar en el código, consulta la [guía de contribución](CONTRIBUTING.md).

## Estructura del código

| Componente | Responsabilidad |
| --- | --- |
| `GasofaPpal` | Verifica la identidad del bot y conecta el catálogo con la recepción de mensajes. |
| `ServicioPrecios` | Descarga y valida el JSON antes de reemplazar la caché y publicar el catálogo en memoria. |
| `Catalogo`, `Gasolinera`, `Combustible` | Interpretan los datos oficiales, calculan distancias y ordenan los precios. |
| `CheapGasofaBot` | Interpreta los mensajes, recuerda la búsqueda asociada a cada teclado y envía el mapa elegido. |

## Cómo busca

El bot compara gasóleo A por defecto. Con una ubicación, empieza en 2,5 km en línea recta y amplía a 5, 10, 20 y 50 km hasta encontrar al menos tres estaciones con precio. Dentro de ese radio muestra las tres más baratas y desempata por distancia. Si hay menos de tres en 50 km, muestra las disponibles.

Las consultas de texto usan un índice de códigos postales, localidades y municipios. Aceptan mayúsculas, tildes y guiones. Si no hay coincidencia exacta, también se busca por el principio de un nombre compuesto y se admite una letra de diferencia en nombres de cinco o más caracteres. Un código postal evita confundir localidades homónimas.

El catálogo del Ministerio se actualiza al arrancar y cada 20 minutos. Si la descarga falla, el bot sigue usando la última copia válida y muestra su fecha. Si arranca sin datos, puede responder a los comandos de ayuda mientras espera la descarga. [Servicio oficial de carburantes](https://sedeaplicaciones.minetur.gob.es/ServiciosRESTCarburantes/PreciosCarburantes/EstacionesTerrestres/)

## Menú de BotFather

Pega estas líneas en `/setcommands` para tu bot:

```text
start - Empezar a buscar gasolineras
help - Ver cómo buscar y cómo se comparan los precios
diesel - Buscar gasóleo A
gasolina95 - Buscar gasolina 95 E5
gasolina98 - Buscar gasolina 98 E5
```

Puedes escribir `/gasolina95 28013` o elegir el carburante en el menú y después compartir la ubicación. La preferencia se guarda en memoria por usuario y chat hasta reiniciar el bot o superar las 10 000 preferencias recientes.

## Licencia

Copyright © 2026 Moisés Pita Merino. [Licencia MIT](LICENSE).
