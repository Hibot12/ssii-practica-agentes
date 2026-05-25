package es.upm.ssii.reagent;

import jade.core.Agent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.Property;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.core.behaviours.FSMBehaviour;
import jade.core.behaviours.OneShotBehaviour;

public class BrokerAgent extends Agent {

    // Identificadores de los Estados de la FSM
    private static final String ESTADO_ESPERAR_UI = "ESPERAR_UI";
    private static final String ESTADO_ESPERAR_SOURCING = "ESPERAR_SOURCING";
    private static final String ESTADO_ESPERAR_ANALISTA = "ESPERAR_ANALISTA";

    // Ontología requerida por el Analista.
    public static final String ONTOLOGY = "ontologia-inmobiliaria";

    private static final String SERVICE_SOURCING = "sourcing";
    private static final String SERVICE_ANALISTA = "analisis-imobilario";

    // Esperamos un REQUEST del UIAgent.
    private MessageTemplate filtroUI = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);

    private AID uiAID;
    private AID sourcingAID;
    private AID analistaAID;
    private String conversationId;
    private String uiReplyWith;
    private String sourcingReplyWith;
    private String analistaReplyWith;

    protected void setup() {
        registrarEnDF();
        AgentsLogger.info("Broker", "Broker activado.");

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
            AgentsLogger.info("Broker", "Esperando interacción en la UI...");

            // Bloquea hasta que llegue filtro de la UI.
            ACLMessage mensajeUI = blockingReceive(filtroUI);

            AgentsLogger.info("Broker","Petición de la UI recibida");

            uiAID = mensajeUI.getSender();
            uiReplyWith = mensajeUI.getReplyWith();
            // Si el UI no manda conversation-id, generamos uno para correlar la cadena.
            conversationId = mensajeUI.getConversationId();
            if (conversationId == null || conversationId.isEmpty()) {
                conversationId = "broker-" + getLocalName() + "-" + System.currentTimeMillis();
            }

            // Forwarding del mensaje hacia SourcingAgent.
            String jsonFiltroRecibido = mensajeUI.getContent();
            sourcingAID = buscarAgentePorServicio(SERVICE_SOURCING);

            if (sourcingAID == null) {
                AgentsLogger.info("Broker","No se ha encontrado el agente de sourcing en el DF.");
                avisarUI(ACLMessage.FAILURE, "No se ha encontrado el agente de sourcing.");
                codigoTransicion = 0;
            } else {
                sourcingReplyWith = "broker-src-" + System.currentTimeMillis();

                ACLMessage peticionSourcing = new ACLMessage(ACLMessage.REQUEST);
                peticionSourcing.addReceiver(sourcingAID);
                peticionSourcing.setContent(jsonFiltroRecibido);
                peticionSourcing.setConversationId(conversationId);
                peticionSourcing.setReplyWith(sourcingReplyWith);

                send(peticionSourcing);
                AgentsLogger.info("Broker","Filtro enviado a SourcingAgent");
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
            codigoTransicion = 0;

            // Solo aceptamos el INFORM del sourcing que responde a nuestra petición.
            MessageTemplate filtroSourcing = MessageTemplate.and(
                    MessageTemplate.and(
                            MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                            MessageTemplate.MatchSender(sourcingAID)),
                    MessageTemplate.and(
                            MessageTemplate.MatchConversationId(conversationId),
                            MessageTemplate.MatchInReplyTo(sourcingReplyWith)));

            ACLMessage listaViviendas = blockingReceive(filtroSourcing);

            if (listaViviendas != null) {
                AgentsLogger.info("Broker","Respuesta recibida del SourcingAgent");

                String contenido = listaViviendas.getContent();

                // Si el contenido es nulo o es un JSON vacío "[]".
                if (contenido == null || contenido.trim().equals("") || contenido.trim().equals("[]")) {
                    AgentsLogger.info("Broker","El SourcingAgent devolvió una lista vacía.");
                    avisarUI(ACLMessage.INFORM,
                            "{\"resultados\":[], \"total\":0, \"mensaje\":\"No se encontraron viviendas con los filtros seleccionados.\"}");

                    codigoTransicion = 0;
                    return;
                }

                analistaAID = buscarAgentePorServicio(SERVICE_ANALISTA);

                if (analistaAID == null) {
                    AgentsLogger.info("Broker","No se ha encontrado el agente analista en el DF.");
                    avisarUI(ACLMessage.FAILURE, "No se ha encontrado el agente analista en el DF.");
                    codigoTransicion = 0;
                } else {
                    analistaReplyWith = "broker-ana-" + System.currentTimeMillis();

                    // Creación del mensaje.
                    ACLMessage peticionAnalista = new ACLMessage(ACLMessage.REQUEST);
                    peticionAnalista.addReceiver(analistaAID);
                    peticionAnalista.setContent(contenido);
                    peticionAnalista.setOntology(ONTOLOGY);
                    peticionAnalista.setConversationId(conversationId);
                    peticionAnalista.setReplyWith(analistaReplyWith);

                    send(peticionAnalista);
                    AgentsLogger.info("Broker","JSON de viviendas transferido al Analista.");
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
                AgentsLogger.info("Broker","Esperando análisis...");

                MessageTemplate filtroAnalista = MessageTemplate.and(
                        MessageTemplate.and(
                                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                                MessageTemplate.MatchOntology(ONTOLOGY)),
                        MessageTemplate.and(
                                MessageTemplate.and(
                                        MessageTemplate.MatchSender(analistaAID),
                                        MessageTemplate.MatchConversationId(conversationId)),
                                MessageTemplate.MatchInReplyTo(analistaReplyWith)));

                ACLMessage informeAnalista = blockingReceive(filtroAnalista);

                if (informeAnalista != null) {
                    AgentsLogger.info("Broker","Informe recibido del Analista.");
                    avisarUI(ACLMessage.INFORM, informeAnalista.getContent());
                    AgentsLogger.info("Broker","Informe enviado de vuelta a la UI.");
                }
            }
        }

        public int onEnd() {
            uiAID = null;
            sourcingAID = null;
            analistaAID = null;
            conversationId = null;
            uiReplyWith = null;
            sourcingReplyWith = null;
            analistaReplyWith = null;
            return 1;
        }
    }

    private void registrarEnDF() {
        DFAgentDescription descripcion = new DFAgentDescription();
        descripcion.setName(getAID());
        descripcion.addLanguages("FIPA-SL");
        descripcion.addOntologies(ONTOLOGY);
        descripcion.addProtocols("fipa-request");

        ServiceDescription servicio = new ServiceDescription();
        servicio.setType("broker");
        servicio.setName(getLocalName());
        servicio.addOntologies(ONTOLOGY);
        servicio.addLanguages("FIPA-SL");
        servicio.addLanguages("JSON");
        servicio.addProtocols("fipa-request");
        Property desc = new Property("Descripción",
                "Agente encargado de la comunicación y gestión de los mensajes");
        servicio.addProperties(desc);
        descripcion.addServices(servicio);

        try {
            DFService.register(this, descripcion);
        } catch (FIPAException e) {
            AgentsLogger.info("Broker","Error al registrar en DF: " + e.getMessage());
        }
    }

    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            AgentsLogger.info("Broker","Error al borrar registro del DF: " + e.getMessage());
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
                AgentsLogger.info("Broker","Error buscando '" + tipoServicio + "' en DF: " + e.getMessage());
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
            if (conversationId != null) {
                mensaje.setConversationId(conversationId);
            }
            if (uiReplyWith != null) {
                mensaje.setInReplyTo(uiReplyWith);
            }
            send(mensaje);
        }
    }

}
