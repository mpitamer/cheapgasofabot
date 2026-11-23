package gasofa.bot;

import java.util.*;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import java.util.function.Supplier;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendVenue;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Atiende en serie las actualizaciones de Telegram. Conserva en memoria la preferencia de cada
 * usuario y la consulta asociada a cada teclado de combustible.
 *
 * @author Moisés Pita Merino
 */
final class CheapGasofaBot implements LongPollingSingleThreadUpdateConsumer {
    private static final String AYUDA = """
            Envía tu ubicación desde el clip de Telegram o escribe una localidad, municipio o código postal.
            Por ubicación busco primero a 2,5 km en línea recta. Si hay menos de tres opciones, amplío a 5, 10, 20 y 50 km.
            Muestro hasta tres estaciones por precio. Pulsa «Obtener ubicación» para recibir su punto en el mapa.

            Pulsa ⇄ junto al nombre de otro combustible para actualizar el listado.
            Empiezas con gasóleo A. Usa /diesel, /gasolina95 o /gasolina98 para elegir carburante.
            También puedes buscar directamente, por ejemplo /gasolina95 28013.""";
    private static final Map<String, Combustible> COMANDOS = Map.of(
            "/diesel", Combustible.DIESEL, "/gasolina95", Combustible.GASOLINA95,
            "/gasolina98", Combustible.GASOLINA98);
    /**
     * Identifica las preferencias de un usuario dentro de un chat, incluidos los chats de grupo.
     */
    private record Sesion(long chat, long usuario) {}

    /**
     * Guarda el origen de una consulta para repetirla con otro combustible. Las coordenadas son nulas
     * en las búsquedas de texto.
     */
    private record Busqueda(Sesion sesion, String lugar, Double latitud, Double longitud) {}
    private final LinkedHashMap<String, Busqueda> busquedas = new LinkedHashMap<>();

    private final TelegramClient telegram;
    private final Supplier<ServicioPrecios.Estado> precios;
    private final String usuarioBot;
    // El consumidor procesa las actualizaciones en serie. Solo guardamos la preferencia reciente.
    private final LinkedHashMap<Sesion, Combustible> preferencias = new LinkedHashMap<>(16, 0.75f, true);

    /**
     * Recibe el transporte, un proveedor que consulta el catálogo vigente y el nombre del bot usado
     * para filtrar comandos de grupo.
     */
    CheapGasofaBot(TelegramClient telegram, Supplier<ServicioPrecios.Estado> precios, String usuarioBot) {
        this.telegram = telegram;
        this.precios = precios;
        this.usuarioBot = usuarioBot;
    }

    /**
     * Distingue las pulsaciones de botones de los mensajes y registra los fallos del transporte sin
     * volcar su contenido.
     */
    @Override
    public void consume(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                var callback = update.getCallbackQuery();
                if (callback.getData() != null && callback.getData().startsWith("fuel:")) cambiarCombustible(callback);
                else enviarUbicacion(callback);
            }
            else if (update.hasMessage()) responder(update.getMessage());
        } catch (TelegramApiException e) {
            System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,
                    "Error de Telegram al responder ({0})", e.getClass().getSimpleName());
        }
    }

    /**
     * Interpreta comandos, texto y ubicaciones. Guarda el contexto del selector después de enviar
     * correctamente el listado.
     */
    private void responder(Message mensaje) throws TelegramApiException {
        long chat = mensaje.getChatId();
        var sesion = new Sesion(chat, mensaje.getFrom() == null ? chat : mensaje.getFrom().getId());
        var tipo = preferencias.getOrDefault(sesion, Combustible.DIESEL);
        String consulta = mensaje.hasText() ? mensaje.getText().strip() : "";
        if (consulta.startsWith("/")) {
            var partes = consulta.split("\\s+", 2);
            var destino = partes[0].toLowerCase(Locale.ROOT).split("@", 2);
            if (destino.length == 2 && !destino[1].equalsIgnoreCase(usuarioBot)) return;
            String comando = destino[0];
            if (comando.equals("/start") || comando.equals("/help")) {
                enviar(chat, AYUDA);
                return;
            }
            tipo = COMANDOS.get(comando);
            if (tipo == null) {
                enviar(chat, "Comando desconocido. Usa /help para ver las opciones.");
                return;
            }
            guardarPreferencia(sesion, tipo);
            if (partes.length == 1) {
                enviar(chat, "Buscaré " + tipo.nombre + ". Envía tu ubicación, localidad o código postal.");
                return;
            }
            consulta = partes[1];
        }
        if (!mensaje.hasLocation() && consulta.isBlank()) return;
        var estado = precios.get();
        var catalogo = estado.catalogo();
        if (catalogo == null) {
            enviar(chat, estado.aviso());
            return;
        }
        var posicion = mensaje.getLocation();
        var busqueda = new Busqueda(sesion, consulta, posicion == null ? null : posicion.getLatitude(),
                posicion == null ? null : posicion.getLongitude());
        String clave = UUID.randomUUID().toString();
        var listado = listado(estado, clave, busqueda, tipo);
        telegram.execute(SendMessage.builder().chatId(chat).parseMode("HTML").text(listado.texto()).replyMarkup(listado.teclado()).build());
        busquedas.put(clave, busqueda);
        if (busquedas.size() > 10_000) busquedas.pollFirstEntry();
    }

    /**
     * Actualiza la elección de la sesión y expulsa la preferencia menos recientemente usada al superar
     * el límite.
     */
    private void guardarPreferencia(Sesion sesion, Combustible tipo) {
        preferencias.put(sesion, tipo);
        if (preferencias.size() > 10_000) preferencias.pollFirstEntry();
    }

    /**
     * Reúne texto y teclado de una misma búsqueda para enviarlos o editarlos juntos.
     */
    private record Listado(String texto, InlineKeyboardMarkup teclado) {}

    /**
     * Calcula las estaciones y numera cada botón según su posición en el texto. El selector de
     * combustible sigue disponible si no hay resultados.
     */
    private Listado listado(ServicioPrecios.Estado estado, String clave, Busqueda busqueda, Combustible tipo) {
        var catalogo = estado.catalogo();
        var resultados = busqueda.latitud() == null ? catalogo.porLugar(busqueda.lugar(), tipo)
                : catalogo.cercanas(busqueda.latitud(), busqueda.longitud(), tipo);
        var texto = new StringBuilder("<b>").append(tipo.nombre).append(" · Opciones por precio</b>");
        if (!estado.aviso().isEmpty()) texto.append("\n<i>").append(estado.aviso()).append("</i>");
        var filas = new ArrayList<InlineKeyboardRow>();
        for (var resultado : resultados) {
            var g = resultado.estacion();
            int numero = filas.size() + 1;
            texto.append("\n\n").append("<b>").append(numero).append(". ").append(ficha(resultado, tipo));
            filas.add(new InlineKeyboardRow(InlineKeyboardButton.builder()
                    .text(numero + " · Obtener ubicación").callbackData("ubic:" + g.id()).build()));
        }

        if (resultados.isEmpty()) {
            texto.append("\n\nNo he encontrado precios de ").append(tipo.nombre)
                    .append(busqueda.latitud() == null ? " para ese lugar." : " en 50 km.");
        }
        texto.append("\n\n<i>Datos del Ministerio · ")
                .append(catalogo.fecha.isBlank() ? "fecha desconocida" : html(catalogo.fecha)).append("</i>");
        var selector = new InlineKeyboardRow();
        for (String comando : List.of("/diesel", "/gasolina95", "/gasolina98")) {
            var alternativa = COMANDOS.get(comando);
            if (alternativa != tipo) {
                selector.add(InlineKeyboardButton.builder().text("⇄ " + alternativa.nombre)
                        .callbackData("fuel:" + clave + ":" + comando).build());
            }
        }
        filas.addFirst(selector);
        return new Listado(texto.toString(), InlineKeyboardMarkup.builder().keyboard(filas).build());
    }

    /**
     * Comprueba que el botón pertenece al usuario y al chat originales, repite la búsqueda y edita el
     * mensaje. Solo entonces guarda la preferencia y confirma el cambio.
     */
    private void cambiarCombustible(CallbackQuery callback) throws TelegramApiException {
        String[] partes = callback.getData().split(":", 3);
        var busqueda = partes.length == 3 ? busquedas.get(partes[1]) : null;
        var tipo = partes.length == 3 ? COMANDOS.get(partes[2]) : null;
        var respuesta = AnswerCallbackQuery.builder().callbackQueryId(callback.getId());
        if (busqueda == null || tipo == null || !(callback.getMessage() instanceof Message mensaje)
                || mensaje.getChatId() != busqueda.sesion().chat()) {
            telegram.execute(respuesta.text("Esta búsqueda ha caducado. Envía de nuevo tu ubicación o localidad.").build());
            return;
        }
        if (callback.getFrom() == null || callback.getFrom().getId() != busqueda.sesion().usuario()) {
            telegram.execute(respuesta.text("Envía tu propia búsqueda para elegir combustible.").build());
            return;
        }
        var estado = precios.get();
        var catalogo = estado.catalogo();
        if (catalogo == null) {
            telegram.execute(respuesta.text("Catálogo no disponible. Inténtalo más tarde.").build());
            return;
        }
        var listado = listado(estado, partes[1], busqueda, tipo);
        String confirmacion = "Combustible cambiado a " + tipo.nombre;
        telegram.execute(EditMessageText.builder().chatId(mensaje.getChatId()).messageId(mensaje.getMessageId())
                .parseMode("HTML").text(listado.texto()).replyMarkup(listado.teclado()).build());
        guardarPreferencia(busqueda.sesion(), tipo);
        telegram.execute(respuesta.text(confirmacion).build());
    }

    /**
     * Resuelve la estación por su identificador en el catálogo vigente. Si continúa disponible, envía
     * su posición y dirección al chat del botón.
     */
    /**
     * Envía respuestas de texto sin teclado, como la ayuda y los avisos de disponibilidad.
     */
    private void enviarUbicacion(CallbackQuery consulta) throws TelegramApiException {
        var estado = precios.get();
        var catalogo = estado.catalogo();
        String datos = consulta.getData();
        var estacion = datos != null && datos.startsWith("ubic:") && catalogo != null
                ? catalogo.estacion(datos.substring(5)) : null;
        var respuesta = AnswerCallbackQuery.builder().callbackQueryId(consulta.getId());
        if (estacion == null || !(consulta.getMessage() instanceof Message mensaje)) {
            telegram.execute(respuesta.text("Ubicación no disponible. Repite la búsqueda.").build());
            return;
        }
        telegram.execute(respuesta.build());
        String direccion = String.join(" ", estacion.direccion(), estacion.cp(), estacion.localidad()).strip();
        telegram.execute(SendVenue.builder().chatId(mensaje.getChatId())
                .latitude(estacion.latitud()).longitude(estacion.longitud()).title(nombreVisible(estacion))
                .address(direccion.isEmpty() ? "Dirección no disponible" : direccion).build());
    }

    /**
     * Compone el resumen de una estación y muestra la distancia solo cuando la búsqueda tiene
     * coordenadas de origen.
     */
    private static String ficha(Catalogo.Resultado resultado, Combustible tipo) {
        var g = resultado.estacion();
        var texto = new StringBuilder(html(nombreVisible(g))).append("</b>\n<b>")
                .append(precio(g, tipo)).append(" € por litro</b>");
        if (Double.isFinite(resultado.distanciaKm())) {
            texto.append(String.format(Locale.forLanguageTag("es-ES"), " · %.2f km en línea recta", resultado.distanciaKm()));
        }
        if (!g.direccion().isBlank()) texto.append("\n").append(html(g.direccion()));
        String localidad = String.join(" ", g.cp(), g.localidad()).strip();
        if (!g.provincia().isBlank()) localidad += localidad.isEmpty() ? g.provincia() : " (" + g.provincia() + ")";
        if (!localidad.isBlank()) texto.append("\n").append(html(localidad));
        if (!g.horario().isBlank()) texto.append("\n<i>Horario · ").append(html(g.horario())).append("</i>");
        return texto.toString();
    }

    /**
     * Proporciona un título legible tanto para el listado como para el mapa cuando falta el rótulo
     * oficial.
     */
    private static String nombreVisible(Gasolinera g) {
        return g.nombre().isBlank() ? "Nombre no disponible" : g.nombre();
    }

    /**
     * Conserva la precisión decimal de la fuente y usa coma decimal en la respuesta al usuario.
     */
    private static String precio(Gasolinera g, Combustible tipo) {
        return g.precios().get(tipo).toPlainString().replace('.', ',');
    }

    private static String html(String valor) {
        return valor.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void enviar(long chat, String texto) throws TelegramApiException {
        telegram.execute(SendMessage.builder().chatId(chat).text(texto).build());
    }
}
