package es.upm.ssii.reagent;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;

public class TestAgent extends Agent {
    protected CyclicBehaviour cyclicBehaviour;

    public void setup() {
        System.out.println("This is a test agent");
        this.cyclicBehaviour = new CyclicBehaviour(this) {
            public void action() {
                block();
            }
        };
        addBehaviour(cyclicBehaviour);
    }
}
