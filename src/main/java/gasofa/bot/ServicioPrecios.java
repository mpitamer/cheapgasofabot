package gasofa.bot;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.*;

/**
 * Mantiene la última copia válida del catálogo en disco y publica su referencia en memoria
 * mediante un campo volátil. Las consultas pueden seguir leyendo durante una descarga.
 *
 * @author Moisés Pita Merino
 */
final class ServicioPrecios implements AutoCloseable {
    private static final URI ORIGEN = URI.create("https://sedeaplicaciones.minetur.gob.es/"
            + "ServiciosRESTCarburantes/PreciosCarburantes/EstacionesTerrestres/");
    private static final System.Logger LOG = System.getLogger(ServicioPrecios.class.getName());
    private final Path cache;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final ScheduledExecutorService tarea = Executors.newSingleThreadScheduledExecutor();
    private volatile Catalogo catalogo;

    /**
     * Fija la ruta de la caché. El temporal se crea en el mismo directorio para permitir el reemplazo
     * atómico.
     */
    ServicioPrecios(Path cache) {
        this.cache = cache.toAbsolutePath();
    }

    /**
     * Carga la caché si existe y programa una primera descarga inmediata. Entre el fin de un intento y
     * el comienzo del siguiente transcurren veinte minutos.
     */
    void iniciar() {
        if (Files.isRegularFile(cache)) {
            try {
                catalogo = leer(cache);
            } catch (IOException e) {
                LOG.log(System.Logger.Level.WARNING, "Copia local ilegible", e);
            }
        }
        tarea.scheduleWithFixedDelay(this::refrescar, 0, 20, TimeUnit.MINUTES);
    }

    /**
     * Devuelve el catálogo publicado o {@code null} mientras todavía no hay ninguna copia válida.
     */
    Catalogo actual() {
        return catalogo;
    }

    /**
     * Abre el JSON en UTF-8 y cierra el lector incluso si la validación falla.
     */
    private static Catalogo leer(Path archivo) throws IOException {
        try (var lector = Files.newBufferedReader(archivo)) {
            return Catalogo.leer(lector);
        }
    }

    /**
     * Descarga en un archivo temporal, valida el contenido y reemplaza la caché antes de publicar el
     * catálogo en memoria. Un fallo conserva la copia anterior.
     */
    void actualizar() throws IOException, InterruptedException {
        var peticion = HttpRequest.newBuilder(ORIGEN).timeout(Duration.ofSeconds(40))
                .header("Accept", "application/json").GET().build();
        Path temporal = Files.createTempFile(cache.getParent(), "precios-", ".tmp");
        try {
            var respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofFile(temporal));
            if (respuesta.statusCode() != 200) throw new IOException("HTTP " + respuesta.statusCode());
            Catalogo nuevo = leer(temporal);
            Files.move(temporal, cache, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            catalogo = nuevo;
        } finally {
            Files.deleteIfExists(temporal);
        }
    }

    /**
     * Aísla los fallos de cada intento para que el planificador siga ejecutando las siguientes
     * descargas.
     */
    private void refrescar() {
        try {
            actualizar();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "No se pudo actualizar el catálogo", e);
        }
    }

    /**
     * Interrumpe la tarea de actualización y cierra el cliente HTTP.
     */
    @Override
    public void close() {
        tarea.shutdownNow();
        http.close();
    }
}
