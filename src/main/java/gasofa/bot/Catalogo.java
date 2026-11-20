package gasofa.bot;

import com.google.gson.*;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Conjunto de estaciones de una descarga. Construye los índices una sola vez y sirve búsquedas por
 * identificador, lugar o distancia hasta la siguiente actualización.
 *
 * @author Moisés Pita Merino
 */
final class Catalogo {
    private static final double[] RADIOS_KM = {2.5, 5, 10, 20, 50};
    private final List<Gasolinera> estaciones;
    private final Map<String, Gasolinera> porId = new HashMap<>();
    private final Map<String, List<Gasolinera>> lugares = new HashMap<>();
    final String fecha;

    /**
     * Agrupa cada estación por municipio, localidad y código postal. Las claves coincidentes de una
     * misma estación se indexan una sola vez.
     */
    private Catalogo(List<Gasolinera> estaciones, String fecha) {
        this.estaciones = List.copyOf(estaciones);
        this.fecha = fecha;
        for (var g : estaciones) {
            porId.put(g.id(), g);
            var claves = new HashSet<>(List.of(normalizar(g.localidad()), normalizar(g.municipio()), g.cp()));
            claves.remove("");
            for (var clave : claves) {
                lugares.computeIfAbsent(clave, k -> new ArrayList<>()).add(g);
            }
        }
    }

    /**
     * Interpreta el JSON del Ministerio. Descarta coordenadas inválidas y precios no positivos o
     * ilegibles. Si falta el identificador oficial, deriva uno de la posición, el rótulo y la
     * dirección.
     * @param reader lector del documento; el llamador se encarga de cerrarlo
     * @return catálogo con al menos una estación válida
     * @throws IOException si el documento no puede interpretarse como catálogo o carece de estaciones utilizables
     */
    static Catalogo leer(Reader reader) throws IOException {
        try {
            var raiz = JsonParser.parseReader(reader).getAsJsonObject();
            var filas = raiz.getAsJsonArray("ListaEESSPrecio");
            if (filas == null) throw new IOException("Falta ListaEESSPrecio");
            var estaciones = new ArrayList<Gasolinera>();
            for (var fila : filas) {
                if (!fila.isJsonObject()) continue;
                var obj = fila.getAsJsonObject();
                double lat = coordenada(obj, "Latitud");
                double lon = coordenada(obj, "Longitud (WGS84)");
                if (!(Math.abs(lat) <= 90 && Math.abs(lon) <= 180)) continue;
                var precios = new EnumMap<Combustible, BigDecimal>(Combustible.class);
                for (var tipo : Combustible.values()) {
                    String valor = campo(obj, tipo.campo);
                    if (valor.isEmpty()) continue;
                    try {
                        var precio = new BigDecimal(valor.replace(',', '.'));
                        if (precio.signum() > 0) precios.put(tipo, precio);
                    } catch (NumberFormatException ignored) {
                        // Un precio incorrecto no invalida los demás carburantes de esta estación.
                    }
                }
                if (!precios.isEmpty()) {
                    String id = campo(obj, "IDEESS");
                    if (!id.matches("[0-9]{1,20}")) {
                        String referencia = lat + "|" + lon + "|" + campo(obj, "Rótulo") + "|" + campo(obj, "Dirección");
                        id = UUID.nameUUIDFromBytes(referencia.getBytes(StandardCharsets.UTF_8)).toString();
                    }
                    estaciones.add(new Gasolinera(id, campo(obj, "Rótulo"), campo(obj, "Dirección"),
                            campo(obj, "Localidad"), campo(obj, "Municipio"), campo(obj, "Provincia"),
                            campo(obj, "C.P."), campo(obj, "Horario"), lat, lon, precios));
                }
            }
            if (estaciones.isEmpty()) throw new IOException("Catálogo sin estaciones válidas");
            return new Catalogo(estaciones, campo(raiz, "Fecha"));
        } catch (JsonParseException | IllegalStateException | ClassCastException e) {
            throw new IOException("Formato de catálogo incorrecto", e);
        }
    }

    /**
     * Lee un valor escalar como texto y devuelve una cadena vacía para campos ausentes o compuestos.
     */
    private static String campo(JsonObject obj, String clave) {
        var valor = obj.get(clave);
        return valor != null && valor.isJsonPrimitive() ? valor.getAsString().strip() : "";
    }

    /**
     * Acepta coma o punto decimal. Devuelve NaN cuando el valor no puede convertirse.
     */
    private static double coordenada(JsonObject obj, String clave) {
        try {
            return Double.parseDouble(campo(obj, clave).replace(',', '.'));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /**
     * Unifica mayúsculas, diacríticos, guiones y espacios para usar la misma clave al indexar y
     * buscar.
     */
    private static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("[\\s-]+", " ").strip();
    }

    /**
     * Asocia la estación con la distancia al usuario. En consultas por nombre o código postal la
     * distancia es NaN.
     */
    record Resultado(Gasolinera estacion, double distanciaKm) {}

    /**
     * Resuelve el identificador guardado en un botón de mapa. Devuelve null si ya no aparece en el
     * catálogo actual.
     */
    Gasolinera estacion(String id) {
        return porId.get(id);
    }

    /**
     * Prioriza la clave exacta. Si falla, admite prefijos de palabras y pequeñas erratas en nombres
     * largos. Los códigos postales conservan sus ceros iniciales y requieren coincidencia exacta.
     */
    List<Resultado> porLugar(String consulta, Combustible tipo) {
        String clave = normalizar(consulta);
        var resultados = new ArrayList<Resultado>();
        var candidatas = lugares.get(clave);
        if (candidatas != null) {
            candidatas.forEach(g -> resultados.add(new Resultado(g, Double.NaN)));
        } else if (clave.length() >= 3 && !clave.matches("\\d+")) {
            var coincidencias = new LinkedHashSet<Gasolinera>();
            lugares.forEach((nombre, grupo) -> {
                if (nombre.startsWith(clave + " ") || (clave.length() >= 5 && aUnaEdicion(clave, nombre))) {
                    coincidencias.addAll(grupo);
                }
            });
            coincidencias.forEach(g -> resultados.add(new Resultado(g, Double.NaN)));
        }
        return tresMasBaratas(resultados, tipo);
    }

    /** Admite una inserción, eliminación o sustitución en los nombres largos. */
    private static boolean aUnaEdicion(String a, String b) {
        if (a.length() > b.length()) return aUnaEdicion(b, a);
        if (b.length() - a.length() > 1) return false;
        int i = 0;
        int j = 0;
        boolean corregido = false;
        while (i < a.length() && j < b.length()) {
            if (a.charAt(i) == b.charAt(j)) {
                i++;
            } else {
                if (corregido) return false;
                corregido = true;
                if (a.length() == b.length()) i++;
            }
            j++;
        }
        return !corregido || j == b.length();
    }

    /** Compara precios en el primer radio con tres ofertas, o en el máximo si hay menos. */
    List<Resultado> cercanas(double lat, double lon, Combustible tipo) {
        var resultados = new ArrayList<Resultado>();
        var porRadio = new int[RADIOS_KM.length];
        for (var g : estaciones) {
            if (!g.precios().containsKey(tipo) || Math.abs(g.latitud() - lat) > 0.46) continue;
            double distancia = g.distanciaKm(lat, lon);
            int radio = 0;
            while (radio < RADIOS_KM.length && distancia > RADIOS_KM[radio]) radio++;
            if (radio == RADIOS_KM.length) continue;
            porRadio[radio]++;
            resultados.add(new Resultado(g, distancia));
        }
        int total = 0;
        for (int i = 0; i < porRadio.length; i++) {
            total += porRadio[i];
            if (total >= 3) {
                double limite = RADIOS_KM[i];
                resultados.removeIf(r -> r.distanciaKm() > limite);
                break;
            }
        }
        return tresMasBaratas(resultados, tipo);
    }

    /**
     * Descarta estaciones sin precio del carburante y limita el resultado a tres. Desempata por
     * distancia, nombre e identificador para obtener un orden reproducible.
     */
    private static List<Resultado> tresMasBaratas(List<Resultado> resultados, Combustible tipo) {
        return resultados.stream().filter(r -> r.estacion().precios().containsKey(tipo))
                .sorted(Comparator.comparing((Resultado r) -> r.estacion().precios().get(tipo))
                        .thenComparingDouble(Resultado::distanciaKm)
                        .thenComparing(r -> r.estacion().nombre()).thenComparing(r -> r.estacion().id()))
                .limit(3).toList();
    }
}
