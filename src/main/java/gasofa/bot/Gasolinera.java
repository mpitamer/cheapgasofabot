package gasofa.bot;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Estación del catálogo con coordenadas en grados y precios en euros por litro. La ausencia de un
 * carburante en el mapa indica que la estación carece de precio válido para él.
 *
 * @author Moisés Pita Merino
 */
record Gasolinera(String id, String nombre, String direccion, String localidad, String municipio,
                  String provincia, String cp, String horario, double latitud, double longitud,
                  Map<Combustible, BigDecimal> precios) {
    /**
     * Copia el mapa de precios para que los cambios del lector del catálogo no afecten a la estación.
     */
    Gasolinera {
        precios = Map.copyOf(precios);
    }

    /**
     * Calcula la distancia de círculo máximo con la fórmula de Haversine y el radio medio terrestre.
     * @param lat latitud del origen en grados
     * @param lon longitud del origen en grados
     * @return distancia en kilómetros sobre la superficie terrestre
     */
    double distanciaKm(double lat, double lon) {
        double senoLat = Math.sin(Math.toRadians(latitud - lat) / 2);
        double senoLon = Math.sin(Math.toRadians(longitud - lon) / 2);
        double a = senoLat * senoLat + Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(latitud))
                * senoLon * senoLon;
        return 6371.0088 * 2 * Math.asin(Math.sqrt(Math.clamp(a, 0, 1)));
    }
}
