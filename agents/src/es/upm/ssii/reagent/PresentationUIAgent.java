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

public class PresentationUIAgent extends Agent {

    protected String userInput = "";
    private transient PresentationFrame myGui;

    @Override
    protected void setup() {
        SwingUtilities.invokeLater(() -> {
            myGui = new PresentationFrame(this);
            myGui.setVisible(true);
        });

        addBehaviour(new BrokerCommunicationBehaviour(this));
    }

    @Override
    protected void takeDown() {
        System.out.println("Agente UI " + getLocalName() + " terminando.");
        if (myGui != null) {
            myGui.dispose();
        }
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
                    SwingUtilities.invokeLater(() -> myGui.appendResult("Resultados:\n" + content + "\n\n"));
                }
            } else {
                SwingUtilities.invokeLater(() -> myGui.appendResult("Error: No se encontró el agente broker.\n\n"));
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
        private JTextField inputField;
        private JTextArea resultArea;

        public PresentationFrame(PresentationUIAgent a) {
            this.myAgent = a;
            setTitle("Buscador de viviendas - " + myAgent.getLocalName());
            setSize(600, 400);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLocationRelativeTo(null);

            JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
            mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JPanel topPanel = new JPanel(new BorderLayout(5, 5));
            inputField = new JTextField();
            JButton btnEnviar = new JButton("Enviar");

            topPanel.add(new JLabel("Filtro (JSON): "), BorderLayout.WEST);
            topPanel.add(inputField, BorderLayout.CENTER);
            topPanel.add(btnEnviar, BorderLayout.EAST);

            resultArea = new JTextArea();
            resultArea.setEditable(false);
            JScrollPane scrollPane = new JScrollPane(resultArea);

            mainPanel.add(topPanel, BorderLayout.NORTH);
            mainPanel.add(scrollPane, BorderLayout.CENTER);
            getContentPane().add(mainPanel);

            btnEnviar.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    myAgent.userInput = inputField.getText();
                    myAgent.doWake();
                }
            });
        }

        public void appendResult(String text) {
            resultArea.append(text);
            resultArea.setCaretPosition(resultArea.getDocument().getLength());
        }
    }
}
