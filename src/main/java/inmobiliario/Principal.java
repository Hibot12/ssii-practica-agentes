package inmobiliario;

import java.util.List;

import inmobiliario.modelo.Vivienda;
import inmobiliario.scraping.ExtractorKyero;

public class Principal {
    public static void main(String[] args) throws Exception {
        ExtractorKyero extractor = new ExtractorKyero();

        List<Vivienda> viviendas = extractor.obtenerViviendas(
                "https://www.kyero.com/en/spain-property-for-sale-0l55529",
                5);

        for (Vivienda v : viviendas) {
            System.out.println(v.titulo);
            System.out.println("Precio: " + v.precio);
            System.out.println("Habitaciones: " + v.habitaciones + "  Baños: " + v.banos + "  m2: " + v.superficieM2);
            System.out.println(v.url);
            System.out.println();
        }
    }
}
