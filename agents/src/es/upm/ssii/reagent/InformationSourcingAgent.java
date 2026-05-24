package es.upm.ssii.reagent;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.Property;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class InformationSourcingAgent extends Agent {

    List<Vivienda> viviendas;
    ExtractorKyero extractor;
    CyclicBehaviour comportamiento;

    private MessageTemplate filtroPeticion = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);

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
                ACLMessage mensaje = receive(filtroPeticion);
                if (mensaje != null) {
                    List<Vivienda> resultado = viviendas;
                    String contenido = mensaje.getContent();
                    if (contenido != null && !contenido.isEmpty()) {
                        try {
                            FiltroVivienda filtro = new Gson().fromJson(contenido, FiltroVivienda.class);
                            if (filtro != null) {
                                resultado = extractor.filtrar(viviendas, filtro);
                            }
                        } catch (JsonSyntaxException e) {
                            System.out.println("Filtro JSON inválido: " + e.getMessage());
                        }
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
        descripcion.addLanguages("FIPA-SL");
        descripcion.addOntologies("ontoloia-inmobiliaria");
        descripcion.addProtocols("fipa-request");

        ServiceDescription servicio = new ServiceDescription();
        servicio.setType("sourcing");
        servicio.setName(getLocalName());
        servicio.addOntologies("ontologia-inmobiliaria");
        servicio.addLanguages("FIPA-SL");
        servicio.addLanguages("JSON");
        servicio.addProtocols("fipa-request");
        Property desc = new Property("Descripción",
                "Agente encargado de la búsqueda y recopilación de datos");
        servicio.addProperties(desc);
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
