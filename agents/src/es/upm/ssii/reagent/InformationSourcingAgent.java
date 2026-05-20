package es.upm.ssii.reagent;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.List;

import com.google.gson.Gson;

public class InformationSourcingAgent extends Agent {

    private List<Vivienda> viviendas;

    protected void setup() {
        ExtractorKyero extractor = new ExtractorKyero();
        try {
            viviendas = extractor.cargarViviendas("viviendas.json");
        } catch (Exception e) {
            System.err.println("InformationSourcingAgent: error cargando viviendas: " + e.getMessage());
            doDelete();
        }
        System.out.println("InformationSourcingAgent: cargadas " + viviendas.size() + " viviendas");
        addBehaviour(new CyclicBehaviour(this) {
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    ACLMessage respuesta = msg.createReply();
                    respuesta.setPerformative(ACLMessage.INFORM);
                    respuesta.setContent(new Gson().toJson(viviendas));
                    send(respuesta);
                } else {
                    block();
                }
            }
        });
    }
}
