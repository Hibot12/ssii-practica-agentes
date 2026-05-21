package es.upm.ssii.reagent;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ExtractorKyero {

    public List<Vivienda> cargarViviendas() throws Exception {
        InputStream stream = getClass().getResourceAsStream("viviendas.json");
        if (stream == null) {
            throw new Exception("No se ha encontrado viviendas.json");
        }
        Type tipo = new TypeToken<List<Vivienda>>(){}.getType();
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, tipo);
        }
    }

    public List<Vivienda> filtrar(List<Vivienda> viviendas, FiltroVivienda filtro) {
        List<Vivienda> resultado = new ArrayList<>();
        for (Vivienda v : viviendas) {
            boolean cumple = true;
            if (filtro.precioMax > 0 && v.precio > filtro.precioMax) {
                cumple = false;
            }
            if (filtro.precioMin > 0 && v.precio < filtro.precioMin) {
                cumple = false;
            }
            if (filtro.habitacionesMin > 0 && v.habitaciones < filtro.habitacionesMin) {
                cumple = false;
            }
            if (filtro.banosMin > 0 && v.banos < filtro.banosMin) {
                cumple = false;
            }
            if (filtro.superficieMin > 0 && v.superficieM2 < filtro.superficieMin) {
                cumple = false;
            }
            if (filtro.superficieMax > 0 && v.superficieM2 > filtro.superficieMax) {
                cumple = false;
            }
            if (filtro.precioM2Min > 0 && v.precioM2 < filtro.precioM2Min) {
                cumple = false;
            }
            if (filtro.precioM2Max > 0 && v.precioM2 > filtro.precioM2Max) {
                cumple = false;
            }
            if (filtro.distanciaAeropuertoMax > 0 && v.distanciaAeropuertoKm > filtro.distanciaAeropuertoMax) {
                cumple = false;
            }
            if (filtro.tipo != null && !v.tipo.equalsIgnoreCase(filtro.tipo)) {
                cumple = false;
            }
            if (filtro.ciudad != null && !v.ciudad.equalsIgnoreCase(filtro.ciudad)) {
                cumple = false;
            }
            if (filtro.provincia != null && !v.provincia.equalsIgnoreCase(filtro.provincia)) {
                cumple = false;
            }
            if (filtro.zona != null && !v.zona.equalsIgnoreCase(filtro.zona)) {
                cumple = false;
            }
            if (filtro.tienePiscina && !v.tienePiscina) {
                cumple = false;
            }
            if (filtro.tieneParking && !v.tieneParking) {
                cumple = false;
            }
            if (filtro.tieneTerraza && !v.tieneTerraza) {
                cumple = false;
            }
            if (filtro.tieneJardin && !v.tieneJardin) {
                cumple = false;
            }
            if (filtro.aireAcondicionado && !v.aireAcondicionado) {
                cumple = false;
            }
            if (filtro.amueblado && !v.amueblado) {
                cumple = false;
            }
            if (filtro.cercaPlaya && !v.cercaPlaya) {
                cumple = false;
            }
            if (cumple) {
                resultado.add(v);
            }
        }
        return resultado;
    }
}
