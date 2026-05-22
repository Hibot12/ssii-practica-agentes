package es.upm.ssii.reagent;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

public class BrokerAgent extends Agent {

    private static final int WAITING_UI = 1;
    private static final int WAITING_SOURCING = 2;
    private static final int WAITING_ANALISTA = 3;

    private int fase = WAITING_UI;

    protected void setup() {
        registrarEnDF();
        System.out.println("[" + getLocalName() + "]: Broker Orquestador activado.");

        // Esperamos un REQUEST del UIAgent.
        MessageTemplate filtroUI = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
        // Esperamos un INFORM como respuesta del InformationSourcingAgent.
        MessageTemplate filtroMensajeViviendas = MessageTemplate.MatchPerformative(ACLMessage.INFORM);

        CyclicBehaviour comportamiento = new CyclicBehaviour(this) {
            public void action() {

                if (fase == WAITING_UI) {
                    System.out.println("[Broker] Esperando interacción en la UI...");

                    // Bloquea hasta que llegue filtro de la UI.
                    ACLMessage mensajeUI = blockingReceive(filtroUI);

                    System.out.println("[Broker] Petición de la UI recivida");

                    // Forwarding del mensaje hacia SourcingAgent.
                    String jsonFiltroRecibido = mensajeUI.getContent();
                    ACLMessage peticionSourcing = new ACLMessage(ACLMessage.REQUEST);
                    peticionSourcing.addReceiver(new AID("sourcing", AID.ISLOCALNAME));
                    peticionSourcing.setContent(jsonFiltroRecibido);

                    send(peticionSourcing);
                    System.out.println("[Broker] Filtro enviado a SourcingAgent");

                    // Transición al estado de espera del recopilador
                    fase = WAITING_SOURCING;
                }

                else if (fase == WAITING_SOURCING) {    
                    ACLMessage listaViviendas = blockingReceive(filtroMensajeViviendas);

                    if (listaViviendas != null) {
                        System.out.println("[Broker] Respuesta recibida del Recopilador");
                        imprimirLista(listaViviendas);

                        // TO DO: Gestionar comunicación con analista.
                        fase = WAITING_ANALISTA;
                    }
                }

            }
        };
        addBehaviour(comportamiento);
    }

    private void registrarEnDF() {
        DFAgentDescription descripcion = new DFAgentDescription();
        descripcion.setName(getAID());

        ServiceDescription servicio = new ServiceDescription();
        servicio.setType("broker");
        servicio.setName(getLocalName());
        descripcion.addServices(servicio);

        try {
            DFService.register(this, descripcion);
        } catch (FIPAException e) {
            System.out.println("[Broker] Error al registrar en DF: " + e.getMessage());
        }
    }

    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            System.out.println("[Broker] Error al borrar registro del DF: " + e.getMessage());
        }
    }

    private void imprimirLista(ACLMessage listaViviendas) {
        try {
            System.out.println("[Resultado del Filtrado JSON]:");
            // Parseamos el texto y lo convertimos a un Array de JSON
            JsonArray lista = JsonParser.parseString(listaViviendas.getContent()).getAsJsonArray();

            for (int i = 0; i < lista.size(); i++) {

                // Extraemos el objeto en la posición i.
                JsonObject elemento = lista.get(i).getAsJsonObject();

                // Sacamos los datos individuales.
                String titulo = elemento.get("titulo").getAsString();
                int precio = elemento.get("precio").getAsInt();

                // Los imprimimos.
                System.out.println("-> Casa " + i + ": " + titulo + " cuesta " + precio + "€");
            }

        } catch (Exception e) {
            System.out.println("[Broker] Error al procesar la lista JSON: " + e.getMessage());
        }
    }
}
