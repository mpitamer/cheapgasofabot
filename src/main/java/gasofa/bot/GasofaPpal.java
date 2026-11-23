package gasofa.bot;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.api.methods.GetMe;

/**
 * Punto de entrada del JAR. Conecta el cliente de Telegram con el catálogo de precios y el
 * consumidor de mensajes.
 *
 * @author Moisés Pita Merino
 */
public final class GasofaPpal {
    /**
     * Obtiene el token del entorno o del archivo local, verifica la identidad del bot e inicia el long
     * polling.
     * El hilo principal permanece en espera mientras se atienden las actualizaciones.
     * @param args argumentos de línea de comandos (se ignoran)
     * @throws Exception si falla la configuración, la conexión inicial o la espera del hilo principal
     */
    public static void main(String[] args) throws Exception {
        String token = System.getenv("TELEGRAM_BOT_TOKEN");
        Path archivo = Path.of(".telegram-token");
        if ((token == null || token.isBlank()) && Files.isRegularFile(archivo)) {
            token = Files.readString(archivo);
        }
        if (token == null || token.isBlank()) {
            try (var recurso = GasofaPpal.class.getResourceAsStream("/.telegram-token")) {
                if (recurso != null) token = new String(recurso.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Configura .telegram-token o TELEGRAM_BOT_TOKEN para iniciar el bot.");
        }
        var cliente = new OkHttpTelegramClient(token.strip());
        try (var precios = new ServicioPrecios(Path.of("descarga.json"));
             var aplicacion = new TelegramBotsLongPollingApplication()) {
            String nombre = cliente.execute(new GetMe()).getUserName();
            precios.iniciar();
            aplicacion.registerBot(token.strip(), new CheapGasofaBot(cliente, precios::actual, nombre));
            System.getLogger(GasofaPpal.class.getName()).log(System.Logger.Level.INFO, "Bot iniciado");
            new CountDownLatch(1).await();
        }
    }
}
