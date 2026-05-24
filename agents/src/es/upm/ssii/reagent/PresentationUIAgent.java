package es.upm.ssii.reagent;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import com.google.gson.Gson;

public class PresentationUIAgent extends Agent {

    Gson gson = new Gson();
    protected String userInput = "";
    private transient PresentationFrame myGui;

    @Override
    protected void setup() {
        registrarEnDF();
        SwingUtilities.invokeLater(() -> {
            myGui = new PresentationFrame(this);
            myGui.setVisible(true);
        });

        addBehaviour(new BrokerCommunicationBehaviour(this));
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            System.out.println("[Broker] Error al borrar registro del DF: " + e.getMessage());
        }
        System.out.println("Agente UI " + getLocalName() + " terminando.");
        if (myGui != null) {
            myGui.dispose();
        }
    }

    private class BrokerResponse {
        public List<Vivienda> resultados;
    }

    private class BrokerCommunicationBehaviour extends CyclicBehaviour {
        public BrokerCommunicationBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            myAgent.doWait();

            AID broker = searchForBroker();

            if (broker != null) {
                ACLMessage req = new ACLMessage(ACLMessage.REQUEST);
                req.addReceiver(broker);
                req.setContent(userInput);
                myAgent.send(req);

                MessageTemplate mt = MessageTemplate.MatchSender(broker);
                ACLMessage reply = myAgent.blockingReceive(mt);

                if (reply != null) {
                    String content = reply.getContent();
                    try {
                        BrokerResponse response = gson.fromJson(content, BrokerResponse.class);
                        SwingUtilities.invokeLater(() -> myGui.updateResults(response.resultados));
                    } catch (Exception e) {
                        e.printStackTrace();
                        SwingUtilities.invokeLater(() -> myGui.showError("Error procesando los resultados JSON."));
                    }
                }
            } else {
                SwingUtilities.invokeLater(() -> myGui.showError("Error: No se encontró el agente broker."));
            }
        }

        private AID searchForBroker() {
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("broker");
            template.addServices(sd);

            try {
                DFAgentDescription[] result = DFService.search(myAgent, template);
                if (result.length > 0) {
                    return result[0].getName();
                }
            } catch (FIPAException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    private class PresentationFrame extends JFrame {
        private PresentationUIAgent myAgent;

        // Carousel State & Components
        private List<Vivienda> currentResults;
        private int currentIndex = 0;
        private JLabel lblTitulo, lblPrecio, lblSuperficie, lblHabitaciones, lblBanos, lblUbicacion;
        private JButton btnPrev, btnNext;

        public PresentationFrame(PresentationUIAgent a) {
            this.myAgent = a;
            setTitle("Buscador de viviendas - " + myAgent.getLocalName());
            setSize(800, 600);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLocationRelativeTo(null);

            JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
            mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            // Grid Layout to house all filter properties cleanly
            JPanel filterPanel = new JPanel(new GridLayout(0, 4, 5, 5));

            // Ints
            JTextField precioMaxF = new JTextField(); filterPanel.add(new JLabel("Precio Max:")); filterPanel.add(precioMaxF);
            JTextField precioMinF = new JTextField(); filterPanel.add(new JLabel("Precio Min:")); filterPanel.add(precioMinF);
            JTextField precioM2MaxF = new JTextField(); filterPanel.add(new JLabel("Precio/m2 Max:")); filterPanel.add(precioM2MaxF);
            JTextField precioM2MinF = new JTextField(); filterPanel.add(new JLabel("Precio/m2 Min:")); filterPanel.add(precioM2MinF);
            JTextField habitacionesMinF = new JTextField(); filterPanel.add(new JLabel("Habitaciones Min:")); filterPanel.add(habitacionesMinF);
            JTextField banosMinF = new JTextField(); filterPanel.add(new JLabel("Baños Min:")); filterPanel.add(banosMinF);
            JTextField superficieMinF = new JTextField(); filterPanel.add(new JLabel("Superficie Min:")); filterPanel.add(superficieMinF);
            JTextField superficieMaxF = new JTextField(); filterPanel.add(new JLabel("Superficie Max:")); filterPanel.add(superficieMaxF);
            JTextField distAeroMaxF = new JTextField(); filterPanel.add(new JLabel("Dist. Aeropuerto Max:")); filterPanel.add(distAeroMaxF);

            // Strings
            JTextField tipoF = new JTextField(); filterPanel.add(new JLabel("Tipo:")); filterPanel.add(tipoF);
            JTextField ciudadF = new JTextField(); filterPanel.add(new JLabel("Ciudad:")); filterPanel.add(ciudadF);
            JTextField provinciaF = new JTextField(); filterPanel.add(new JLabel("Provincia:")); filterPanel.add(provinciaF);
            JTextField zonaF = new JTextField(); filterPanel.add(new JLabel("Zona:")); filterPanel.add(zonaF);

            // Booleans (Using Standard Checkboxes to avoid text parsing errors)
            JCheckBox tienePiscinaC = new JCheckBox(); filterPanel.add(new JLabel("Tiene Piscina:")); filterPanel.add(tienePiscinaC);
            JCheckBox tieneParkingC = new JCheckBox(); filterPanel.add(new JLabel("Tiene Parking:")); filterPanel.add(tieneParkingC);
            JCheckBox tieneTerrazaC = new JCheckBox(); filterPanel.add(new JLabel("Tiene Terraza:")); filterPanel.add(tieneTerrazaC);
            JCheckBox tieneJardinC = new JCheckBox(); filterPanel.add(new JLabel("Tiene Jardin:")); filterPanel.add(tieneJardinC);
            JCheckBox aireAcondC = new JCheckBox(); filterPanel.add(new JLabel("Aire Acondicionado:")); filterPanel.add(aireAcondC);
            JCheckBox amuebladoC = new JCheckBox(); filterPanel.add(new JLabel("Amueblado:")); filterPanel.add(amuebladoC);
            JCheckBox cercaPlayaC = new JCheckBox(); filterPanel.add(new JLabel("Cerca Playa:")); filterPanel.add(cercaPlayaC);

            JButton btnEnviar = new JButton("Enviar");

            JPanel topPanel = new JPanel(new BorderLayout());
            topPanel.add(filterPanel, BorderLayout.CENTER);
            topPanel.add(btnEnviar, BorderLayout.SOUTH);

            // -------------------------------------------------------------
            // NEW CAROUSEL PANEL (Replaces JTextArea and JScrollPane)
            // -------------------------------------------------------------
            JPanel resultContainer = new JPanel(new BorderLayout());
            resultContainer.setBorder(BorderFactory.createTitledBorder("Resultados"));

            JPanel labelsPanel = new JPanel(new GridLayout(6, 1, 5, 5));
            lblTitulo = new JLabel("Esperando búsqueda...", SwingConstants.CENTER);
            lblPrecio = new JLabel("", SwingConstants.CENTER);
            lblSuperficie = new JLabel("", SwingConstants.CENTER);
            lblHabitaciones = new JLabel("", SwingConstants.CENTER);
            lblBanos = new JLabel("", SwingConstants.CENTER);
            lblUbicacion = new JLabel("", SwingConstants.CENTER);

            // Styling adjustments for emphasis
            lblTitulo.setFont(new Font("SansSerif", Font.BOLD, 14));
            lblPrecio.setForeground(new Color(0, 100, 0));

            labelsPanel.add(lblTitulo);
            labelsPanel.add(lblPrecio);
            labelsPanel.add(lblSuperficie);
            labelsPanel.add(lblHabitaciones);
            labelsPanel.add(lblBanos);
            labelsPanel.add(lblUbicacion);

            btnPrev = new JButton("<");
            btnNext = new JButton(">");
            btnPrev.setEnabled(false);
            btnNext.setEnabled(false);

            resultContainer.add(btnPrev, BorderLayout.WEST);
            resultContainer.add(labelsPanel, BorderLayout.CENTER);
            resultContainer.add(btnNext, BorderLayout.EAST);

            // Carousel Actions
            btnPrev.addActionListener(e -> {
                if (currentIndex > 0) {
                    currentIndex--;
                    displayCurrentResult();
                }
            });

            btnNext.addActionListener(e -> {
                if (currentResults != null && currentIndex < currentResults.size() - 1) {
                    currentIndex++;
                    displayCurrentResult();
                }
            });

            mainPanel.add(topPanel, BorderLayout.NORTH);
            mainPanel.add(resultContainer, BorderLayout.CENTER);
            getContentPane().add(mainPanel);

            btnEnviar.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    FiltroVivienda f = new FiltroVivienda();

                    // Parse values
                    try { f.precioMax = Integer.parseInt(precioMaxF.getText().trim()); } catch(Exception ex) {}
                    try { f.precioMin = Integer.parseInt(precioMinF.getText().trim()); } catch(Exception ex) {}
                    try { f.precioM2Max = Integer.parseInt(precioM2MaxF.getText().trim()); } catch(Exception ex) {}
                    try { f.precioM2Min = Integer.parseInt(precioM2MinF.getText().trim()); } catch(Exception ex) {}
                    try { f.habitacionesMin = Integer.parseInt(habitacionesMinF.getText().trim()); } catch(Exception ex) {}
                    try { f.banosMin = Integer.parseInt(banosMinF.getText().trim()); } catch(Exception ex) {}
                    try { f.superficieMin = Integer.parseInt(superficieMinF.getText().trim()); } catch(Exception ex) {}
                    try { f.superficieMax = Integer.parseInt(superficieMaxF.getText().trim()); } catch(Exception ex) {}
                    try { f.distanciaAeropuertoMax = Integer.parseInt(distAeroMaxF.getText().trim()); } catch(Exception ex) {}

                    f.tipo = tipoF.getText().trim().isEmpty() ? null : tipoF.getText().trim();
                    f.ciudad = ciudadF.getText().trim().isEmpty() ? null : ciudadF.getText().trim();
                    f.provincia = provinciaF.getText().trim().isEmpty() ? null : provinciaF.getText().trim();
                    f.zona = zonaF.getText().trim().isEmpty() ? null : zonaF.getText().trim();

                    f.tienePiscina = tienePiscinaC.isSelected();
                    f.tieneParking = tieneParkingC.isSelected();
                    f.tieneTerraza = tieneTerrazaC.isSelected();
                    f.tieneJardin = tieneJardinC.isSelected();
                    f.aireAcondicionado = aireAcondC.isSelected();
                    f.amueblado = amuebladoC.isSelected();
                    f.cercaPlaya = cercaPlayaC.isSelected();

                    // Map via GSON and alert broker
                    myAgent.userInput = gson.toJson(f);
                    myAgent.doWake();
                }
            });
        }

        public void updateResults(List<Vivienda> resultados) {
            this.currentResults = resultados;
            this.currentIndex = 0;
            displayCurrentResult();
        }

        public void showError(String msg) {
            this.currentResults = null;
            lblTitulo.setText(msg);
            lblPrecio.setText("");
            lblSuperficie.setText("");
            lblHabitaciones.setText("");
            lblBanos.setText("");
            lblUbicacion.setText("");
            btnPrev.setEnabled(false);
            btnNext.setEnabled(false);
        }

        private void displayCurrentResult() {
            if (currentResults == null || currentResults.isEmpty()) {
                showError("No se encontraron resultados para su filtro.");
                return;
            }

            Vivienda v = currentResults.get(currentIndex);

            // Format labels with current data
            String headerTitle = v.titulo != null && !v.titulo.isEmpty() ? v.titulo : ("Vivienda ID: " + v.id);
            lblTitulo.setText("[" + (currentIndex + 1) + " / " + currentResults.size() + "] " + headerTitle);

            lblPrecio.setText("Precio: " + v.precio + " €  (" + v.precioM2 + " €/m²)");
            lblSuperficie.setText("Superficie: " + v.superficieM2 + " m²");
            lblHabitaciones.setText("Habitaciones: " + v.habitaciones);
            lblBanos.setText("Baños: " + v.banos);

            String ubi = (v.ciudad != null ? v.ciudad : "") +
                         (v.zona != null && !v.zona.isEmpty() ? " - " + v.zona : "");
            lblUbicacion.setText(ubi.isEmpty() ? "Ubicación desconocida" : "Ubicación: " + ubi);

            // Toggle arrow states based on bounds
            btnPrev.setEnabled(currentIndex > 0);
            btnNext.setEnabled(currentIndex < currentResults.size() - 1);
        }
    }

    private void registrarEnDF() {
        DFAgentDescription descripcion = new DFAgentDescription();
        descripcion.setName(getAID());

        ServiceDescription servicio = new ServiceDescription();
        servicio.setType("ui");
        servicio.setName(getLocalName());
        descripcion.addServices(servicio);

        try {
            DFService.register(this, descripcion);
        } catch (FIPAException e) {
            System.out.println("[Broker] Error al registrar en DF: " + e.getMessage());
        }
    }
}
