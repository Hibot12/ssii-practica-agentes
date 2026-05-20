package inmobiliario;

import java.util.List;

import inmobiliario.modelo.Vivienda;
import inmobiliario.scraping.ExtractorKyero;

public class Principal {
    public static void main(String[] args) throws Exception {
        ExtractorKyero extractor = new ExtractorKyero();
        long inicio = System.currentTimeMillis();

        List<Vivienda> viviendas = extractor.cargarViviendas("viviendas.json");

        for (Vivienda v : viviendas) {
            System.out.println(v.titulo);
            System.out.println("Precio: " + v.precio + " EUR");
            System.out.print("Habitaciones: " + v.habitaciones);
            if (v.banos > 0) {
                System.out.print("  Baños: " + v.banos);
            }
            if (v.superficieM2 > 0) {
                System.out.print("  m2: " + v.superficieM2);
            }
            System.out.println();
            System.out.println(v.url);
            System.out.println();
        }

        long segundos = (System.currentTimeMillis() - inicio) / 1000;
        System.out.println("Tiempo: " + segundos + "s (" + viviendas.size() + " viviendas)");
    }
}
