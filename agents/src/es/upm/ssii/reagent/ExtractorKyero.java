package es.upm.ssii.reagent;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ExtractorKyero {

    public List<Vivienda> cargarViviendas(String archivo) throws Exception {
        String json = Files.readString(Path.of(archivo));
        Type tipo = new TypeToken<List<Vivienda>>(){}.getType();
        return new Gson().fromJson(json, tipo);
    }
}
