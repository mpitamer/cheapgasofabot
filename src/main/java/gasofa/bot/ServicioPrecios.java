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
    private static final URI ORIGEN = URI.create("https://energia.serviciosmin.gob.es/"
            + "ServiciosRESTCarburantes/PreciosCarburantes/EstacionesTerrestres/");
    private static final System.Logger LOG = System.getLogger(ServicioPrecios.class.getName());
    private final Path cache;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final ScheduledExecutorService tarea = Executors.newSingleThreadScheduledExecutor();
    /** Publica el catálogo y su aviso juntos para que cada consulta sea coherente. */
    record Estado(Catalogo catalogo, String aviso) {}
    private volatile Estado estado = new Estado(null, "Estoy descargando los precios del Ministerio. Inténtalo en unos instantes.");
    private final URI origen;

    /**
     * Fija la ruta de la caché. El temporal se crea en el mismo directorio para permitir el reemplazo
     * atómico.
     */
    ServicioPrecios(Path cache) {
        this(cache, ORIGEN);
    }

    ServicioPrecios(Path cache, URI origen) {
        this.origen = origen;
        this.cache = cache.toAbsolutePath();
    }

    /**
     * Carga la caché si existe y programa una primera descarga inmediata. Entre el fin de un intento y
     * el comienzo del siguiente transcurren veinte minutos.
     */
    void iniciar() {
        cargarCopia();
        tarea.scheduleWithFixedDelay(this::refrescar, 0, 20, TimeUnit.MINUTES);
    }

    void cargarCopia() {
        if (Files.isRegularFile(cache)) {
            try {
                estado = new Estado(leer(cache), "Mostrando la última copia guardada mientras se consultan los precios.");
            } catch (IOException e) {
                LOG.log(System.Logger.Level.WARNING, "Copia local ilegible", e);
            }
        }
    }

    /**
     * Devuelve una instantánea del catálogo disponible y del estado de la última actualización.
     */
    Estado actual() {
        return estado;
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
        var peticion = HttpRequest.newBuilder(origen).timeout(Duration.ofSeconds(40))
                .header("Accept", "application/json").GET().build();
        Path temporal = Files.createTempFile(cache.getParent(), "precios-", ".tmp");
        try {
            var respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofFile(temporal));
            if (respuesta.statusCode() != 200) throw new IOException("HTTP " + respuesta.statusCode());
            Catalogo nuevo = leer(temporal);
            Files.move(temporal, cache, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            estado = new Estado(nuevo, "");
        } finally {
            Files.deleteIfExists(temporal);
        }
    }

    /**
     * Aísla los fallos de cada intento para que el planificador siga ejecutando las siguientes
     * descargas.
     */
    void refrescar() {
        try {
            actualizar();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException e) {
            var anterior = estado.catalogo();
            estado = new Estado(anterior, anterior == null
                    ? "No he podido actualizar los precios del Ministerio y aún no hay una copia disponible. Vuelve a intentarlo más tarde."
                    : "No he podido actualizar los precios del Ministerio. Te muestro los últimos datos guardados.");
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
