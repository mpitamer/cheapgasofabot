package gasofa.bot;

import java.io.IOException;
import java.io.StringReader;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static gasofa.bot.Combustible.*;

/**
 * Pruebas de lectura, selección geográfica y ordenación con datos locales controlados.
 *
 * @author Moisés Pita Merino
 */
class CatalogoTest {
    /**
     * Construye estaciones con empates, carburantes ausentes y valores inválidos para probar los
     * límites de la búsqueda.
     */
    static Catalogo catalogo() throws IOException {
        return Catalogo.leer(new StringReader("""
                {"Fecha":"20/11/2026","ListaEESSPrecio":[
                  {"Rótulo":"Lejos","Latitud":"40,03","Longitud (WGS84)":"-3","Municipio":"Ávila",
                   "C.P.":"05001","Precio Gasoleo A":"1,10","Precio Gasolina 98 E5":"1,55"},
                  {"Rótulo":"Norte","Latitud":"40,02","Longitud (WGS84)":"-3","Municipio":"Ávila",
                   "C.P.":"05001","Precio Gasoleo A":"1,40","Precio Gasolina 95 E5":"1,5"},
                  {"Rótulo":"Centro","Latitud":"40","Longitud (WGS84)":"-3","Localidad":"San-José",
                   "C.P.":"05002","Precio Gasoleo A":"1,400","Precio Gasolina 95 E5":""},
                  {"Rótulo":"Diagonal","Latitud":"40,019","Longitud (WGS84)":"-2,981","Localidad":"San-José",
                   "Precio Gasoleo A":"1,00","Precio Gasolina 95 E5":"-1"},
                  {"Rótulo":"Solo 95","Latitud":"40","Longitud (WGS84)":"-3","Localidad":"San-José",
                   "Precio Gasoleo A":"error","Precio Gasolina 95 E5":"1,3"},
                  {"Rótulo":"Fuera del radio","Latitud":"40,08","Longitud (WGS84)":"-3","Precio Gasoleo A":"0,90"},
                  {"Latitud":"NaN","Longitud (WGS84)":"0","Precio Gasoleo A":"0,01"}]}
                """));
    }

    /**
     * Verifica que el precio se compara dentro del radio elegido y que el empate favorece a la
     * estación más próxima.
     */
    @Test void comparaDentroDelPrimerRadioYOrdenaLosEmpatesPorDistancia() throws IOException {
        var encontrados = catalogo().cercanas(40, -3, DIESEL);
        assertEquals(java.util.List.of("Diagonal", "Lejos", "Centro"),
                encontrados.stream().map(r -> r.estacion().nombre()).toList());
        assertEquals(0, encontrados.getLast().distanciaKm(), 0.005);
    }

    /**
     * Comprueba la ampliación del radio y el resultado vacío cuando se supera la distancia máxima.
     */
    @Test void ampliaElRadioSoloSiFaltaElCarburante() throws IOException {
        assertEquals("Lejos", catalogo().cercanas(40, -3, GASOLINA98).getFirst().estacion().nombre());
        assertTrue(catalogo().cercanas(42, -3, DIESEL).isEmpty());
    }

    /**
     * Comprueba las variantes de nombres y que la consulta postal conserva los ceros iniciales.
     */
    @Test void admiteAcentosGuionesErroresYCerosIniciales() throws IOException {
        var catalogo = catalogo();
        for (var lugar : java.util.List.of("san jose", "San-José", "san josa")) {
            assertEquals("Solo 95", catalogo.porLugar(lugar, GASOLINA95).getFirst().estacion().nombre());
        }
        assertEquals("Lejos", catalogo.porLugar("avila", DIESEL).getFirst().estacion().nombre());
        assertEquals("Centro", catalogo.porLugar("05002", DIESEL).getFirst().estacion().nombre());
        assertTrue(catalogo.porLugar("05002", GASOLINA95).isEmpty());
        assertTrue(catalogo.porLugar("5002", DIESEL).isEmpty());
    }

    /**
     * Evita que una descarga vacía o incompleta se acepte como catálogo.
     */
    @Test void rechazaDescargasVaciasOTruncadas() {
        for (String json : java.util.List.of("{}", "{", "{\"ListaEESSPrecio\":[]}")) {
            assertThrows(IOException.class, () -> Catalogo.leer(new StringReader(json)));
        }
    }
}
