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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import inmobiliario.modelo.Vivienda;

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

    public Vivienda extraerVivienda(String urlVivienda) throws Exception {
        String html = descargarPagina(urlVivienda);
        Document doc = Jsoup.parse(html, urlVivienda);

        Vivienda vivienda = new Vivienda();
        vivienda.url = urlVivienda;
        vivienda.titulo = doc.selectFirst("h1").text();
        vivienda.descripcion = doc.selectFirst("meta[name=description]").attr("content");

        for (Element script : doc.select("script[type=application/ld+json]")) {
            JsonObject json = JsonParser.parseString(script.data()).getAsJsonObject();

            if (json.has("price")) {
                vivienda.precio = json.get("price").getAsInt();
            }
            if (json.has("numberOfBedrooms")) {
                vivienda.habitaciones = json.get("numberOfBedrooms").getAsInt();
            }
            if (json.has("numberOfBathroomsTotal")) {
                vivienda.banos = json.get("numberOfBathroomsTotal").getAsInt();
            }
            if (json.has("floorSize")) {
                JsonObject tamanyo = json.get("floorSize").getAsJsonObject();
                vivienda.superficieM2 = tamanyo.get("value").getAsInt();
            }
            if (json.has("addressLocality")) {
                vivienda.ciudad = json.get("addressLocality").getAsString();
            }
        }

        return vivienda;
    }

    public List<Vivienda> obtenerViviendas(String urlBusqueda, int maxResultados) throws Exception {
        List<String> enlaces = obtenerEnlacesViviendas(urlBusqueda, maxResultados);
        List<Vivienda> viviendas = new ArrayList<>();
        for (String enlace : enlaces) {
            Vivienda v = extraerVivienda(enlace);
            if (v.habitaciones > 0) {
                viviendas.add(v);
            }
        }
        return viviendas;
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
