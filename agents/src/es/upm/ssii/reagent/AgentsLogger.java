package es.upm.ssii.reagent;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;

public class AgentsLogger {
   
    private static final Logger logger = Logger.getLogger("AgentsCustomLogger");

    static {
        try {
            logger.setUseParentHandlers(false);
            logger.setLevel(Level.ALL);

            FileHandler fileHandler = new FileHandler("agents.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            fileHandler.setLevel(Level.ALL);
            
            logger.addHandler(fileHandler);

        } catch (Exception e) {
            System.err.println("[AgentsLogger] Error configurando el archivo logs.txt: " + e.getMessage());
        }
    }

    // Al llamar a estos métodos, SOLO se escribirá en nuestro fileHandler (logs.txt)
    public static void info(String tag, String mensaje) {
        logger.info("[" + tag + "] " + mensaje);
    }

    public static void warning(String tag, String mensaje) {
        logger.warning("[" + tag + "] ALERTA: " + mensaje);
    }

    public static void severe(String tag, String mensaje) {
        logger.severe("[" + tag + "] CRÍTICO: " + mensaje);
    }
}