package es.upm.ssii.reagent;

import jade.core.Agent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
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

    private AID uiAID;
    private AID sourcingAID;
    private AID analistaAID;

    protected void setup() {
        registrarEnDF();
        System.out.println("[" + getLocalName() + "]: Broker activado.");

        // Instanciamos el contenedor de la máquina de estados.
        FSMBehaviour fsm = new FSMBehaviour(this);

        // Registrar los estados individuales con sus comportamientos.
        fsm.registerFirstState(new EsperarUIBehaviour(), ESTADO_ESPERAR_UI);
        fsm.registerState(new EsperarSourcingBehaviour(), ESTADO_ESPERAR_SOURCING);
        fsm.registerState(new EsperarAnalistaBehaviour(), ESTADO_ESPERAR_ANALISTA);

        // Definición de transiciones.
        // Cada estado devuelve 1 al terminar con éxito para avanzar al
        // siguiente
        fsm.registerTransition(ESTADO_ESPERAR_UI, ESTADO_ESPERAR_SOURCING, 1);
        fsm.registerTransition(ESTADO_ESPERAR_UI, ESTADO_ESPERAR_UI, 0);

        fsm.registerTransition(ESTADO_ESPERAR_SOURCING, ESTADO_ESPERAR_ANALISTA, 1);
        fsm.registerTransition(ESTADO_ESPERAR_SOURCING, ESTADO_ESPERAR_UI, 0);

        fsm.registerTransition(ESTADO_ESPERAR_ANALISTA, ESTADO_ESPERAR_UI, 1);

        // Lanzamos la FSM
        addBehaviour(fsm);
    }

    public class EsperarUIBehaviour extends OneShotBehaviour {
        private int codigoTransicion;
        public void action() {
            System.out.println("[Broker] Esperando interacción en la UI...");

            // Bloquea hasta que llegue filtro de la UI.
            ACLMessage mensajeUI = blockingReceive(filtroUI);

            System.out.println("[Broker] Petición de la UI recibida");

            uiAID = mensajeUI.getSender();

            // Forwarding del mensaje hacia SourcingAgent.
            String jsonFiltroRecibido = mensajeUI.getContent();
            sourcingAID = buscarAgentePorServicio("information-sourcing");

            if (sourcingAID == null) {
                System.out.println("[Broker] No se ha encontrado el agente de information-sourcing en el DF.");
                avisarUI(ACLMessage.FAILURE, "No se ha encontrado el agente de information-sourcing.");
                codigoTransicion = 0;
            } else {
                ACLMessage peticionSourcing = new ACLMessage(ACLMessage.REQUEST);
                peticionSourcing.addReceiver(sourcingAID);
                peticionSourcing.setContent(jsonFiltroRecibido);

                send(peticionSourcing);
                System.out.println("[Broker] Filtro enviado a SourcingAgent");
                codigoTransicion = 1;
            }
        }

        public int onEnd() {
            return codigoTransicion;
        }
    }

    public class EsperarSourcingBehaviour extends OneShotBehaviour {
        private int codigoTransicion;

        public void action() {
            ACLMessage listaViviendas = blockingReceive(filtroMensajeViviendas);

            if (listaViviendas != null) {
                System.out.println("[Broker] Respuesta recibida del SourcingAgent");

                String contenido = listaViviendas.getContent();

                // Si el contenido es nulo o es un JSON vacío "[]".
                if (contenido == null || contenido.trim().equals("") || contenido.trim().equals("[]")) {
                    System.out.println("[Broker] El SourcingAgent devolvió una lista vacía.");
                    avisarUI(ACLMessage.INFORM,
                            "{\"resultados\":[], \"total\":0, \"mensaje\":\"No se encontraron viviendas con los filtros seleccionados.\"}");

                    codigoTransicion = 0;
                    return;
                } 

                imprimirLista(listaViviendas);

                analistaAID = buscarAgentePorServicio("analista");

                if (analistaAID == null) {
                    System.out.println("[Broker] No se ha encontrado el agente analista en el DF.");
                    avisarUI(ACLMessage.FAILURE, "No se ha encontrado el agente analista en el DF.");
                    codigoTransicion = 0;
                } else {
                    // Creación del mensaje.
                    ACLMessage peticionAnalista = new ACLMessage(ACLMessage.REQUEST);
                    peticionAnalista.addReceiver(analistaAID);
                    peticionAnalista.setContent(contenido);
                    peticionAnalista.setOntology(ONTOLOGY);

                    send(peticionAnalista);
                    System.out.println("[Broker] JSON de viviendas transferido al Analista.");
                    codigoTransicion = 1;
                }
            }
        }

        public int onEnd() {
            return codigoTransicion;
        }
    }

    public class EsperarAnalistaBehaviour extends OneShotBehaviour {
        public void action() {
            if (analistaAID != null) {
                System.out.println("[Broker] Esperando análisis...");

                MessageTemplate filtroAnalista = MessageTemplate.and(
                        MessageTemplate.and(
                                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                                MessageTemplate.MatchOntology(ONTOLOGY)),
                        MessageTemplate.MatchSender(analistaAID));

                ACLMessage informeAnalista = blockingReceive(filtroAnalista);

                if (informeAnalista != null) {
                    System.out.println("[Broker] Informe recibido del Analista.");
                    // System.out.println("[Contenido del Reporte]: " +
                    // informeAnalista.getContent());

                    if (uiAID != null) {
                        ACLMessage respuestaUI = new ACLMessage(ACLMessage.INFORM);
                        respuestaUI.addReceiver(uiAID);
                        respuestaUI.setContent(informeAnalista.getContent());
                        respuestaUI.setOntology(ONTOLOGY);

                        send(respuestaUI);
                        System.out.println("[Broker] Informe enviado de vuelta a la UI.");
                    } else {
                        System.out.println("[Broker] Error: No se pudo identificar al agente UI de origen.");
                    }

                }
            }
        }

        public int onEnd() {
            uiAID = null;
            sourcingAID = null;
            analistaAID = null;
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

    // Reintenta por si el otro agente aún no se ha registrado.
    private AID buscarAgentePorServicio(String tipoServicio) {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType(tipoServicio);
        template.addServices(sd);

        AID resultado = null;
        boolean encontrado = false;
        int intento = 0;
        while (!encontrado && intento < 10) {
            try {
                DFAgentDescription[] busqueda = DFService.search(this, template);
                if (busqueda != null && busqueda.length > 0) {
                    resultado = busqueda[0].getName();
                    encontrado = true;
                }
            } catch (FIPAException e) {
                System.out.println("[Broker] Error buscando '" + tipoServicio + "' en DF: " + e.getMessage());
            }
            if (!encontrado) {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    encontrado = true;
                }
            }
            intento++;
        }
        return resultado;
    }

    private void avisarUI(int performativa, String contenido) {
        if (uiAID != null) {
            ACLMessage mensaje = new ACLMessage(performativa);
            mensaje.addReceiver(uiAID);
            mensaje.setContent(contenido);
            mensaje.setOntology(ONTOLOGY);
            send(mensaje);
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
