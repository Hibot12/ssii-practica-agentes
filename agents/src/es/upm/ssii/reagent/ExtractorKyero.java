package es.upm.ssii.reagent;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ExtractorKyero {

    public List<Vivienda> cargarViviendas(String archivo) throws Exception {
        String json = Files.readString(Path.of(archivo));
        Type tipo = new TypeToken<List<Vivienda>>(){}.getType();
        return new Gson().fromJson(json, tipo);
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
            if (filtro.ciudad != null && !v.ciudad.equalsIgnoreCase(filtro.ciudad)) {
                cumple = false;
            }
            if (filtro.provincia != null && !v.provincia.equalsIgnoreCase(filtro.provincia)) {
                cumple = false;
            }
            if (filtro.tienePiscina && !v.tienePiscina) {
                cumple = false;
            }
            if (filtro.tieneParking && !v.tieneParking) {
                cumple = false;
            }
            if (cumple) {
                resultado.add(v);
            }
        }
        return resultado;
    }
}
