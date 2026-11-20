package gasofa.bot;

/**
 * Relaciona los carburantes del catálogo con sus etiquetas en español y con las claves exactas de
 * la fuente de precios.
 *
 * @author Moisés Pita Merino
 */
enum Combustible {
    DIESEL("Gasóleo A", "Gasoleo A"),
    GASOLINA95("Gasolina 95 E5"),
    GASOLINA98("Gasolina 98 E5"),
    DIESEL_PREMIUM("Gasóleo Premium", "Gasoleo Premium"),
    DIESEL_B("Gasóleo B", "Gasoleo B"),
    GASOLINA95_E10("Gasolina 95 E10"),
    GASOLINA95_PREMIUM("Gasolina 95 E5 Premium"),
    GASOLINA98_E10("Gasolina 98 E10");

    final String nombre;
    final String campo;

    /**
     * Usa el mismo nombre para la etiqueta y para el sufijo de la clave JSON.
     */
    Combustible(String nombre) {
        this(nombre, nombre);
    }

    /**
     * Conserva la grafía de la fuente aunque difiera de la etiqueta mostrada, como ocurre con «Gasoleo
     * A».
     */
    Combustible(String nombre, String campo) {
        this.nombre = nombre;
        this.campo = "Precio " + campo;
    }
}
