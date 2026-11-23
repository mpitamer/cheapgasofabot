package gasofa.bot;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Prueba caídas y recuperación con un servidor HTTP local y una caché independiente. */
class ServicioPreciosTest {
    @TempDir Path carpeta;

    @Test void conservaLaUltimaCopiaTrasFallosYSeRecupera() throws Exception {
        var codigo = new AtomicInteger(503);
        var cuerpo = new AtomicReference<>("error");
        var servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", intercambio -> {
            byte[] bytes = cuerpo.get().getBytes(StandardCharsets.UTF_8);
            try (intercambio) {
                intercambio.sendResponseHeaders(codigo.get(), bytes.length);
                intercambio.getResponseBody().write(bytes);
            }
        });
        servidor.start();
        var ruta = carpeta.resolve("descarga.json");
        var uri = URI.create("http://127.0.0.1:" + servidor.getAddress().getPort());
        try (var servicio = new ServicioPrecios(ruta, uri)) {
            Files.writeString(ruta, "{");
            servicio.cargarCopia();
            servicio.refrescar();
            assertNull(servicio.actual().catalogo());
            assertTrue(servicio.actual().aviso().contains("no hay una copia"));
            String valido = """
                    {"Fecha":"lunes","ListaEESSPrecio":[{"Latitud":"40","Longitud (WGS84)":"-3","Precio Gasoleo A":"1,52"}]}
                    """;
            cuerpo.set(valido);
            codigo.set(200);
            servicio.refrescar();
            var ultimo = servicio.actual().catalogo();
            assertEquals("lunes", ultimo.fecha);
            for (String error : new String[]{"HTTP", "{", "{\"ListaEESSPrecio\":[]}"}) {
                codigo.set(error.equals("HTTP") ? 503 : 200);
                cuerpo.set(error);
                servicio.refrescar();
                assertSame(ultimo, servicio.actual().catalogo());
                assertTrue(servicio.actual().aviso().contains("últimos datos"));
                assertEquals(valido, Files.readString(ruta));
            }
            try (var reinicio = new ServicioPrecios(ruta, uri)) {
                reinicio.cargarCopia();
                assertEquals("lunes", reinicio.actual().catalogo().fecha);
                assertFalse(reinicio.actual().aviso().isBlank());
            }
            cuerpo.set(valido.replace("lunes", "martes"));
            servicio.refrescar();
            assertEquals("martes", servicio.actual().catalogo().fecha);
            assertTrue(servicio.actual().aviso().isEmpty());
        } finally {
            servidor.stop(0);
        }
    }
}
