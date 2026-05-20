package inmobiliario;

import java.util.List;

import inmobiliario.modelo.Vivienda;
import inmobiliario.scraping.ExtractorKyero;

public class Principal {
    public static void main(String[] args) throws Exception {
        ExtractorKyero extractor = new ExtractorKyero();

        List<Vivienda> viviendas = extractor.obtenerViviendas(
                "https://www.kyero.com/en/valencia-property-for-sale-0l53632",
                5
        );

        for (Vivienda v : viviendas) {
            System.out.println(v.titulo + " - " + v.url);
        }
    }
}
