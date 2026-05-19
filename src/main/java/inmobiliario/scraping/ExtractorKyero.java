package inmobiliario.scraping;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class ExtractorKyero {

    public List<String> obtenerEnlacesViviendas(String urlBusqueda, int maxResultados) throws Exception {
        String html = descargarPagina(urlBusqueda);
        Document doc = Jsoup.parse(html, urlBusqueda);
        List<Element> tarjetas = doc.select("a[data-testid=property-tile]");

        int total = Math.min(maxResultados, tarjetas.size());
        List<String> enlaces = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            enlaces.add(tarjetas.get(i).absUrl("href"));
        }
        return enlaces;
    }

    private String descargarPagina(String url) throws Exception {
        HttpClient cliente = HttpClient.newHttpClient();
        HttpRequest peticion = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                .build();
        return cliente.send(peticion, HttpResponse.BodyHandlers.ofString()).body();
    }
}
