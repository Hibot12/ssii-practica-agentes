package es.upm.ssii.reagent;

import org.json.JSONObject;

/*Clase para repasar el filtro de Weka y que no pasen 
errores analizando la descripción de los pisos*/

public class ProcesadorTexto {

    public String procesarVivienda(JSONObject jsonVivienda, String prediccionWeka) {

        String id = jsonVivienda.getString("id");
        int precio = jsonVivienda.getInt("precio");
        int precioM2 = jsonVivienda.getInt("precioM2");
        int superficie = jsonVivienda.getInt("superficieM2");
        String ciudad = jsonVivienda.optString("ciudad", "");
        String zona = jsonVivienda.optString("zona", "");
        int habitaciones = jsonVivienda.getInt("habitaciones");
        int banos = jsonVivienda.getInt("banos");
        boolean tienePiscina = jsonVivienda.getBoolean("tienePiscina");
        boolean tieneTerraza = jsonVivienda.getBoolean("tieneTerraza");
        boolean cercaPlaya = jsonVivienda.getBoolean("cercaPlaya");
        double distanciaAero = jsonVivienda.getDouble("distanciaAeropuertoKm");
        String descripcion = jsonVivienda.optString("descripcion", "").toLowerCase();

        int puntuacionNLP = 0;
        boolean filtro = false;
        boolean necesitaReforma = false;

        // Búsqueda de palabas que mandan el piso directamente a descarte
        if (descripcion.contains("okupa") || descripcion.contains("squat")
                || descripcion.contains("derribo") || descripcion.contains("demolish")
                || descripcion.contains("subasta") || descripcion.contains("auction")
                || descripcion.contains("destruido") || descripcion.contains("destroyed")
                || descripcion.contains("illegal") || descripcion.contains("plot only")) {
            filtro = true;
        }

        /*
         * Búsquedas de palabras que denotan que un piso necesita reforma o que esta
         * reformado
         */
        boolean paraReformar = descripcion.contains("para reformar")
                || descripcion.contains("to renovate") || descripcion.contains("to refurbish")
                || descripcion.contains("renovation needed") || descripcion.contains("needs updating")
                || descripcion.contains("needs renovation") || descripcion.contains("needs reform")
                || descripcion.contains("antiguo") || descripcion.contains("dated")
                || descripcion.contains("humedades") || descripcion.contains("damp")
                || descripcion.contains("obras") || descripcion.contains("project")
                || descripcion.contains("fixer") || descripcion.contains("ruin");

        boolean reformado = descripcion.contains("nuevo") || descripcion.contains("new build")
                || descripcion.contains("reformado") || descripcion.contains("renovated")
                || descripcion.contains("refurbished") || descripcion.contains("recently renovated")
                || descripcion.contains("newly built") || descripcion.contains("turnkey")
                || descripcion.contains("modern design") || descripcion.contains("high standard");

        // Ajuste de puntuación para pisos que están reformados o necesitan reforma
        if (paraReformar && !reformado) {
            necesitaReforma = true;
            puntuacionNLP -= 20;
        } else if (reformado) {
            puntuacionNLP += 20;
        }

        // Búsqueda de palabras positivas que suben la puntuación global del piso
        if (descripcion.contains("luminoso") || descripcion.contains("luz natural")
                || descripcion.contains("bright") || descripcion.contains("natural light")
                || descripcion.contains("sun-drenched") || descripcion.contains("light-filled")) {
            puntuacionNLP += 10;
        }
        if (descripcion.contains("espacioso") || descripcion.contains("amplio")
                || descripcion.contains("spacious") || descripcion.contains("generous")) {
            puntuacionNLP += 10;
        }
        if (descripcion.contains("buenas vistas") || descripcion.contains("vista al mar")
                || descripcion.contains("sea view") || descripcion.contains("panoramic view")
                || descripcion.contains("mountain view") || descripcion.contains("stunning view")) {
            puntuacionNLP += 10;
        }
        if (descripcion.contains("zona tranquila") || descripcion.contains("poco ruido")
                || descripcion.contains("quiet area") || descripcion.contains("peaceful")
                || descripcion.contains("tranquil")) {
            puntuacionNLP += 5;
        }

        // ── Additional positive signals ──
        if (descripcion.contains("investment") || descripcion.contains("rental income")
                || descripcion.contains("inversion") || descripcion.contains("rentabilidad")) {
            puntuacionNLP += 10;
        }
        if (descripcion.contains("luxury") || descripcion.contains("lujo")
                || descripcion.contains("exclusive") || descripcion.contains("premium")) {
            puntuacionNLP += 5;
        }
        if (descripcion.contains("key ready") || descripcion.contains("move in")
                || descripcion.contains("listo para entrar")) {
            puntuacionNLP += 5;
        }

        // Búsqueda de palabras negativas que bajan la puntuación global del piso
        if (descripcion.contains("tenant") || descripcion.contains("occupied")
                || descripcion.contains("inquilino")) {
            puntuacionNLP -= 10;
        }
        if (descripcion.contains("no parking") || descripcion.contains("no garage")) {
            puntuacionNLP -= 5;
        }

        // Accede a la predicción hecha por la ML y ajusta la clase en función a la
        // puntuación calculada
        String claseAsig = prediccionWeka;

        if (filtro) {
            claseAsig = ":Descartado";
        } else if (necesitaReforma) {
            claseAsig = ":ParaReformar";
        } else if (puntuacionNLP >= 20 && (claseAsig.equals(":Vivienda") || claseAsig.equals("Vivienda"))) {
            claseAsig = ":Oferta";
        } else if (puntuacionNLP <= -15 && (claseAsig.equals(":Oferta") || claseAsig.equals("Oferta"))) {
            claseAsig = ":Vivienda";
        }

        // Asegura que las clases sigan la estructura de la ontología
        if (!claseAsig.startsWith(":")) {
            claseAsig = ":" + claseAsig;
        }

        // Genera las tripletas de la ontología
        StringBuilder ttl = new StringBuilder();

        ttl.append(":").append(id).append(" rdf:type ").append(claseAsig).append(" ;\n");
        ttl.append("    :precio \"").append(precio).append("\"^^xsd:integer ;\n");
        ttl.append("    :precioM2 \"").append(precioM2).append("\"^^xsd:integer ;\n");
        ttl.append("    :superficieM2 \"").append(superficie).append("\"^^xsd:integer ;\n");
        ttl.append("    :habitaciones \"").append(habitaciones).append("\"^^xsd:integer ;\n");
        ttl.append("    :banos \"").append(banos).append("\"^^xsd:integer ;\n");
        ttl.append("    :tienePiscina \"").append(tienePiscina).append("\"^^xsd:boolean ;\n");
        ttl.append("    :tieneTerraza \"").append(tieneTerraza).append("\"^^xsd:boolean ;\n");
        ttl.append("    :cercaPlaya \"").append(cercaPlaya).append("\"^^xsd:boolean ;\n");
        ttl.append("    :distanciaAeropuerto \"").append(distanciaAero).append("\"^^xsd:decimal .\n\n");

        ttl.append(":").append(id).append(" :tieneUbicacion :Ubicacion_").append(id).append(" .\n");
        ttl.append(":Ubicacion_").append(id).append(" :ciudad \"").append(ciudad).append("\"^^xsd:string .\n\n");

        return ttl.toString();
    }

    // Puntuación del piso sin generar la ontlogía en caso de que el analista lo
    // necesite
    public int calcularPuntuacionNLP(String descripcion) {
        if (descripcion == null)
            return 0;
        descripcion = descripcion.toLowerCase();
        int score = 0;

        // Rasgos positivos
        if (descripcion.contains("bright") || descripcion.contains("luminoso"))
            score += 10;
        if (descripcion.contains("spacious") || descripcion.contains("amplio"))
            score += 10;
        if (descripcion.contains("sea view") || descripcion.contains("vista al mar"))
            score += 10;
        if (descripcion.contains("renovado") || descripcion.contains("nueva construcción"))
            score += 20;
        if (descripcion.contains("silencioso") || descripcion.contains("sin ruido"))
            score += 5;
        if (descripcion.contains("inversion") || descripcion.contains("alquiler"))
            score += 10;
        if (descripcion.contains("lujo") || descripcion.contains("exclusivo"))
            score += 5;

        // Rasgos negativos
        if (descripcion.contains(" necesita reforma") || descripcion.contains("project"))
            score -= 15;
        if (descripcion.contains("ruina") || descripcion.contains("antiguo"))
            score -= 20;
        if (descripcion.contains("squat") || descripcion.contains("demolicion"))
            score -= 50;
        if (descripcion.contains("okupa") || descripcion.contains("occupied"))
            score -= 10;

        return score;
    }
}
