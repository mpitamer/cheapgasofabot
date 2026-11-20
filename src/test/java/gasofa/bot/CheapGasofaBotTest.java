package gasofa.bot;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.User;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.send.SendVenue;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueba el diálogo y los botones interceptando las llamadas al cliente de Telegram, sin
 * credenciales ni tráfico externo.
 *
 * @author Moisés Pita Merino
 */
class CheapGasofaBotTest {
    /**
     * Simula la búsqueda, dos cambios de combustible y la selección del mapa. Comprueba el orden y el
     * contenido de las operaciones enviadas.
     */
    @Test void procesaComandosUbicacionYTextoSinConectarATelegram() throws Exception {
        var enviados = new ArrayList<Object>();
        var cliente = (TelegramClient) Proxy.newProxyInstance(TelegramClient.class.getClassLoader(),
                new Class<?>[]{TelegramClient.class}, (proxy, metodo, args) -> {
                    enviados.add(args[0]);
                    return null;
                });
        var catalogo = CatalogoTest.catalogo();
        var bot = new CheapGasofaBot(cliente, () -> catalogo, "cheap_bot");
        bot.consume(mensaje("\"location\":{\"latitude\":40,\"longitude\":-3}"));
        assertEquals(1, enviados.size());
        var menu = assertInstanceOf(SendMessage.class, enviados.getFirst());
        var teclado = ((InlineKeyboardMarkup) menu.getReplyMarkup()).getKeyboard();
        assertEquals(4, teclado.size());
        var cambio = new CallbackQuery();
        cambio.setId("combustible");
        cambio.setFrom(User.builder().id(3L).firstName("Ana").isBot(false).build());
        cambio.setMessage(mensaje("\"text\":\"listado\"").getMessage());
        cambio.setData(teclado.getFirst().getFirst().getCallbackData());
        var pulsacion = new Update();
        pulsacion.setCallbackQuery(cambio);
        enviados.clear();
        bot.consume(pulsacion);
        var actualizado = assertInstanceOf(EditMessageText.class, enviados.getFirst());
        assertTrue(actualizado.getText().contains("Gasolina 95 E5"));
        assertTrue(actualizado.getText().contains("2. Norte"));
        assertTrue(assertInstanceOf(AnswerCallbackQuery.class, enviados.getLast()).getText().contains("cambiado"));
        cambio.setData(actualizado.getReplyMarkup().getKeyboard().getFirst().getLast().getCallbackData());
        enviados.clear();
        bot.consume(pulsacion);
        assertTrue(assertInstanceOf(EditMessageText.class, enviados.getFirst()).getText().contains("Gasolina 98 E5"));
        enviados.clear();
        bot.consume(new Update());
        bot.consume(mensaje("\"text\":\"/diesel@otro_bot\""));
        assertTrue(enviados.isEmpty());
        bot.consume(mensaje("\"text\":\"/gasolina95@cheap_bot san jose\""));
        assertEquals(1, enviados.size());
        assertTrue(((SendMessage) enviados.getFirst()).getText().contains("1. Solo 95"));
        enviados.clear();
        bot.consume(mensaje("\"location\":{\"latitude\":40,\"longitude\":-3}"));
        assertEquals(1, enviados.size());
        var listado = (SendMessage) enviados.getFirst();
        assertTrue(listado.getText().contains("2. Norte"));
        var filas = ((InlineKeyboardMarkup) listado.getReplyMarkup()).getKeyboard();
        assertEquals(3, filas.size());
        var consulta = new CallbackQuery();
        consulta.setId("seleccion");
        consulta.setData(filas.get(2).getFirst().getCallbackData());
        consulta.setMessage(mensaje("\"text\":\"listado\"").getMessage());
        var seleccion = new Update();
        seleccion.setCallbackQuery(consulta);
        enviados.clear();
        bot.consume(seleccion);
        assertEquals(2, enviados.size());
        assertInstanceOf(AnswerCallbackQuery.class, enviados.getFirst());
        var lugar = assertInstanceOf(SendVenue.class, enviados.getLast());
        assertEquals("Norte", lugar.getTitle());
        assertEquals(40.02, lugar.getLatitude());
        consulta.setData("ubic:inexistente");
        enviados.clear();
        bot.consume(seleccion);
        assertEquals(1, enviados.size());
        assertTrue(assertInstanceOf(AnswerCallbackQuery.class, enviados.getFirst()).getText().contains("Repite"));
        enviados.clear();
        bot.consume(mensaje("\"text\":\"05002\""));
        assertTrue(((SendMessage) enviados.getFirst()).getText().contains("No he encontrado"));
    }

    /**
     * Deserializa un mensaje mínimo con la misma estructura que recibe el consumidor desde Telegram.
     */
    private Update mensaje(String contenido) throws Exception {
        String json = """
                {"update_id":1,"message":{"message_id":2,"date":1795170000,"chat":{"id":3,"type":"private"},%s}}
                """.formatted(contenido);
        return new ObjectMapper().readValue(json, Update.class);
    }
}
