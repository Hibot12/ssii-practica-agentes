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
import jade.core.behaviours.FSMBehaviour;
import jade.core.behaviours.OneShotBehaviour;

public class BrokerAgent extends Agent {

    // Identificadores de los Estados de la FSM
    private static final String ESTADO_ESPERAR_UI = "ESPERAR_UI";
    private static final String ESTADO_ESPERAR_SOURCING = "ESPERAR_SOURCING";
    private static final String ESTADO_ESPERAR_ANALISTA = "ESPERAR_ANALISTA";

    // Ontología requerida por el Analista.
    public static final String ONTOLOGY = "ontologia-imobilaria";

    // Esperamos un REQUEST del UIAgent.
    private MessageTemplate filtroUI = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
    // Esperamos un INFORM como respuesta del InformationSourcingAgent.
    private MessageTemplate filtroMensajeViviendas = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
    // Filtro analista.
    private MessageTemplate filtroAnalista = MessageTemplate.and(
            MessageTemplate.MatchPerformative(ACLMessage.INFORM),
            MessageTemplate.MatchOntology(ONTOLOGY));

    protected void setup() {
        registrarEnDF();
        System.out.println("[" + getLocalName() + "]: Broker Orquestador activado.");

        // Instanciamos el contenedor de la máquina de estados.
        FSMBehaviour fsm = new FSMBehaviour(this);

        // Registrar los estados individuales con sus comportamientos.
        fsm.registerFirstState(new EsperarUIBehaviour(), ESTADO_ESPERAR_UI);
        fsm.registerState(new EsperarSourcingBehaviour(), ESTADO_ESPERAR_SOURCING);
        fsm.registerState(new EsperarAnalistaBehaviour(), ESTADO_ESPERAR_ANALISTA);

        // Definición de transiciones.
        // Cada estado devuelve siempre 1 al terminar con éxito para avanzar al
        // siguiente
        fsm.registerTransition(ESTADO_ESPERAR_UI, ESTADO_ESPERAR_SOURCING, 1);
        fsm.registerTransition(ESTADO_ESPERAR_SOURCING, ESTADO_ESPERAR_ANALISTA, 1);
        fsm.registerTransition(ESTADO_ESPERAR_ANALISTA, ESTADO_ESPERAR_UI, 1);

        // Lanzamos la FSM
        addBehaviour(fsm);
    }

    public class EsperarUIBehaviour extends OneShotBehaviour {
        public void action() {
            System.out.println("[Broker] Esperando interacción en la UI...");

            // Bloquea hasta que llegue filtro de la UI.
            // ACLMessage mensajeUI = blockingReceive(filtroUI);

            System.out.println("[Broker] Petición de la UI recivida");

            // Forwarding del mensaje hacia SourcingAgent.
            // String jsonFiltroRecibido = mensajeUI.getContent();
            FiltroVivienda jsonFiltroRecibidoJava = new FiltroVivienda();
            jsonFiltroRecibidoJava.tipo = "Apartment";
            jsonFiltroRecibidoJava.ciudad = "Javea";
            Gson gson = new Gson();
            String jsonFiltroRecibido = gson.toJson(jsonFiltroRecibidoJava);

            ACLMessage peticionSourcing = new ACLMessage(ACLMessage.REQUEST);
            peticionSourcing.addReceiver(new AID("sourcing", AID.ISLOCALNAME));
            peticionSourcing.setContent(jsonFiltroRecibido);

            send(peticionSourcing);
            System.out.println("[Broker] Filtro enviado a SourcingAgent");
        }

        public int onEnd() {
            return 1;
        }
    }

    public class EsperarSourcingBehaviour extends OneShotBehaviour {
        public void action() {
            ACLMessage listaViviendas = blockingReceive(filtroMensajeViviendas);

            if (listaViviendas != null) {
                System.out.println("[Broker] Respuesta recibida del Recopilador");
                imprimirLista(listaViviendas);

                // Creación del mensaje.
                ACLMessage peticionAnalista = new ACLMessage(ACLMessage.REQUEST);
                peticionAnalista.addReceiver(new AID("analista", AID.ISLOCALNAME));
                peticionAnalista.setContent(listaViviendas.getContent());
                peticionAnalista.setOntology(ONTOLOGY);

                send(peticionAnalista);
                System.out.println("[Broker] JSON de viviendas transferido al Analista.");

            }
        }

        public int onEnd() {
            return 1;
        }
    }

    public class EsperarAnalistaBehaviour extends OneShotBehaviour {
        public void action() {
            System.out.println("[Broker] Esperando análisis...");

            // Bloquea hasta que el Analista envie informe.
            ACLMessage informeAnalista = blockingReceive(filtroAnalista);

            if (informeAnalista != null) {
                System.out.println("[Broker] ¡ÉXITO! Informe recibido del Analista.");
                System.out.println("[Contenido del Reporte]: " + informeAnalista.getContent());
            }
        }

        public int onEnd() {
            return 1;
        }
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
