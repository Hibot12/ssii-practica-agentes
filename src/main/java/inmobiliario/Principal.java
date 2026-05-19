package inmobiliario;

import java.util.List;

import inmobiliario.modelo.Vivienda;
import inmobiliario.scraping.ExtractorKyero;

public class Principal {
    public static void main(String[] args) throws Exception {
        ExtractorKyero extractor = new ExtractorKyero();

        List<String> enlaces = extractor.obtenerEnlacesViviendas(
                "https://www.kyero.com/en/valencia-property-for-sale-0l53632",
                5
        );

        Vivienda v = extractor.extraerVivienda(enlaces.get(0));

        System.out.println(v.titulo);
        System.out.println(v.descripcion);
        System.out.println(v.url);
    }
}
