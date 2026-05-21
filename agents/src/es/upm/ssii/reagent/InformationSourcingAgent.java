package es.upm.ssii.reagent;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.List;

import com.google.gson.Gson;

public class InformationSourcingAgent extends Agent {

    List<Vivienda> viviendas;
    ExtractorKyero extractor;
    CyclicBehaviour comportamiento;

    public void setup() {
        extractor = new ExtractorKyero();
        try {
            viviendas = extractor.cargarViviendas("viviendas.json");
            System.out.println("Cargadas " + viviendas.size() + " viviendas");
        } catch (Exception e) {
            System.out.println("Error al cargar viviendas: " + e.getMessage());
        }
        comportamiento = new CyclicBehaviour(this) {
            public void action() {
                ACLMessage mensaje = receive();
                if (mensaje != null) {
                    List<Vivienda> resultado = viviendas;
                    String contenido = mensaje.getContent();
                    if (contenido != null && !contenido.isEmpty()) {
                        FiltroVivienda filtro = new Gson().fromJson(contenido, FiltroVivienda.class);
                        resultado = extractor.filtrar(viviendas, filtro);
                    }
                    ACLMessage respuesta = mensaje.createReply();
                    respuesta.setPerformative(ACLMessage.INFORM);
                    String json = new Gson().toJson(resultado);
                    respuesta.setContent(json);
                    send(respuesta);
                } else {
                    block();
                }
            }
        };
        addBehaviour(comportamiento);
    }
}
