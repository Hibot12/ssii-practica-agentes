package es.upm.ssii.reagent;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.List;

import com.google.gson.Gson;

public class InformationSourcingAgent extends Agent {

    List<Vivienda> viviendas;
    CyclicBehaviour comportamiento;

    public void setup() {
        ExtractorKyero extractor = new ExtractorKyero();
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
                    ACLMessage respuesta = mensaje.createReply();
                    respuesta.setPerformative(ACLMessage.INFORM);
                    String json = new Gson().toJson(viviendas);
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
