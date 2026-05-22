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
    protected void setup() {
        registrarEnDF();
        System.out.println("[" + getLocalName() + "]: Broker Orquestador activado.");

        // Esperamos un INFORM como respuesta del InformationSourcingAgent.
        MessageTemplate filtroMensajeViviendas = MessageTemplate.MatchPerformative(ACLMessage.INFORM);

        CyclicBehaviour comportamiento = new CyclicBehaviour(this) {
            public void action() {
                ACLMessage listaViviendas = blockingReceive(filtroMensajeViviendas);

                if (listaViviendas != null) {
                    System.out.println("[Broker] Respuesta recibida del Recopilador");
                    System.out.println("[Resultado del Filtrado JSON]:");
                    imprimirLista(listaViviendas);
                }
            }
        };
        addBehaviour(comportamiento);

        // Filtro estático.
        FiltroVivienda filtroEstatico = new FiltroVivienda();
        filtroEstatico.tipo = "Apartment";
        filtroEstatico.habitacionesMin = 3;
        filtroEstatico.tienePiscina = true;
        String jsonFiltro = new Gson().toJson(filtroEstatico);

        // Enviamos petición.
        ACLMessage peticion = new ACLMessage(ACLMessage.REQUEST);
        peticion.addReceiver(new AID("sourcing", AID.ISLOCALNAME));
        peticion.setContent(jsonFiltro);

        System.out.println("[Broker] Enviando Filtro Estático (Piso, >=3 habs, Piscina) a 'sourcing'...");
        send(peticion);
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
