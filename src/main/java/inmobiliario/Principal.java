package inmobiliario;

import java.util.List;

import inmobiliario.scraping.ExtractorKyero;

public class Principal {
    public static void main(String[] args) throws Exception {
        ExtractorKyero extractor = new ExtractorKyero();

        List<String> enlaces = extractor.obtenerEnlacesViviendas(
                "https://www.kyero.com/en/valencia-property-for-sale-0l53632",
                5
        );

        System.out.println("Viviendas encontradas:");
        for (String enlace : enlaces) {
            System.out.println("- " + enlace);
        }
    }
}
