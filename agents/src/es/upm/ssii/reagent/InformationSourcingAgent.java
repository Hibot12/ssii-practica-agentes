package es.upm.ssii.reagent;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;

public class InformationSourcingAgent extends Agent {

    List<Vivienda> viviendas;
    ExtractorKyero extractor;
    CyclicBehaviour comportamiento;

    public void setup() {
        registrarEnDF();
        extractor = new ExtractorKyero();
        try {
            viviendas = extractor.cargarViviendas();
            System.out.println("Cargadas " + viviendas.size() + " viviendas");
        } catch (Exception e) {
            viviendas = new ArrayList<>();
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

    private void registrarEnDF() {
        DFAgentDescription descripcion = new DFAgentDescription();
        descripcion.setName(getAID());

        ServiceDescription servicio = new ServiceDescription();
        servicio.setType("information-sourcing");
        servicio.setName(getLocalName());
        descripcion.addServices(servicio);

        try {
            DFService.register(this, descripcion);
        } catch (FIPAException e) {
            System.out.println("Error al registrar en DF: " + e.getMessage());
        }
    }

    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            System.out.println("Error al borrar registro del DF: " + e.getMessage());
        }
    }
}
