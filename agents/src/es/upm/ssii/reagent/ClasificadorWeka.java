package es.upm.ssii.reagent;

import weka.classifiers.trees.J48;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Weka-based classifier for real estate properties.
 *
 * Trains a J48 decision tree on startup from an ARFF file, then classifies
 * individual Vivienda objects into one of four ontology classes:
 * :Oferta — good investment opportunity
 * :Vivienda — normal property, fair price
 * :ParaReformar — cheap but needs renovation
 * :Descartado — overpriced or unviable
 *
 * The prediction is returned as ":Oferta", ":Vivienda", etc. to match
 * the ontology prefixes used by ProcesadorTexto.
 *
 * Usage:
 * ClasificadorWeka clf = new ClasificadorWeka("data/viviendas_training.arff");
 * String clase = clf.clasificar(viviendaJson);
 */
public class ClasificadorWeka {

    private J48 tree;
    private Instances datasetStructure;
    private boolean ready = false;

    // The class values matching the ontology
    private static final List<String> CLASSES = Arrays.asList(
            "Oferta", "Vivienda", "ParaReformar", "Descartado");

    /**
     * Loads training data and builds the J48 decision tree.
     *
     * @param arffStream stream of the ARFF training file
     */
    public ClasificadorWeka(InputStream arffStream) {
        try {
            DataSource source = new DataSource(arffStream);
            Instances data = source.getDataSet();

            // Last attribute is the class
            data.setClassIndex(data.numAttributes() - 1);

            // Train J48 decision tree
            tree = new J48();
            tree.setUnpruned(false);
            tree.setConfidenceFactor(0.25f);
            tree.setMinNumObj(2);
            tree.buildClassifier(data);

            // Keep the structure for building new instances
            datasetStructure = new Instances(data, 0);
            ready = true;

            AgentsLogger.info("ClasificadorWeka", "Model trained successfully.");
            AgentsLogger.info("ClasificadorWeka", tree.toSummaryString());

        } catch (Exception e) {
            AgentsLogger.severe("ClasificadorWeka", "Failed to train model: " + e.getMessage());
            AgentsLogger.severe("ClasificadorWeka", "Will fall back to rule-based classification.");
            ready = false;
        }
    }

    /**
     * Classifies a Vivienda based on its numeric/boolean features.
     *
     * @return ontology class string: ":Oferta", ":Vivienda", ":ParaReformar", or
     *         ":Descartado"
     */
    public String clasificar(Vivienda v) {
        if (!ready) {
            return clasificarFallback(v);
        }

        try {
            Instance instance = buildInstance(v);
            double prediction = tree.classifyInstance(instance);
            String className = datasetStructure.classAttribute().value((int) prediction);
            return ":" + className;
        } catch (Exception e) {
            System.err.println("[ClasificadorWeka] Classification error: " + e.getMessage());
            return clasificarFallback(v);
        }
    }

    /**
     * Returns the probability distribution across all classes.
     * Useful for the AnalystAgent to show confidence levels.
     *
     * @return array of [Oferta_prob, Vivienda_prob, ParaReformar_prob,
     *         Descartado_prob]
     */
    public double[] distribucion(Vivienda v) {
        if (!ready) {
            return new double[] { 0.25, 0.25, 0.25, 0.25 };
        }
        try {
            Instance instance = buildInstance(v);
            return tree.distributionForInstance(instance);
        } catch (Exception e) {
            return new double[] { 0.25, 0.25, 0.25, 0.25 };
        }
    }

    /**
     * Builds a Weka Instance from a Vivienda object, matching the ARFF attributes.
     */
    private Instance buildInstance(Vivienda v) {
        Instance inst = new DenseInstance(datasetStructure.numAttributes());
        inst.setDataset(datasetStructure);

        inst.setValue(0, v.precioM2); // precioM2
        inst.setValue(1, v.superficieM2); // superficieM2
        inst.setValue(2, v.habitaciones); // habitaciones
        inst.setValue(3, v.banos); // banos
        inst.setValue(4, v.tienePiscina ? "true" : "false"); // tienePiscina
        inst.setValue(5, v.tieneTerraza ? "true" : "false"); // tieneTerraza
        inst.setValue(6, v.tieneParking ? "true" : "false"); // tieneParking
        inst.setValue(7, v.tieneJardin ? "true" : "false"); // tieneJardin
        inst.setValue(8, v.aireAcondicionado ? "true" : "false"); // aireAcondicionado
        inst.setValue(9, v.cercaPlaya ? "true" : "false"); // cercaPlaya
        inst.setValue(10, v.distanciaAeropuertoKm); // distanciaAeropuertoKm

        return inst;
    }

    /**
     * Rule-based fallback when Weka model is not available.
     * Uses simple thresholds that approximate the decision tree.
     */
    private String clasificarFallback(Vivienda v) {
        // Descartado: tiny or extremely overpriced
        if (v.superficieM2 < 35 || v.precioM2 > 4000) {
            return ":Descartado";
        }
        // ParaReformar: very cheap per m2, few amenities
        int amenities = countAmenities(v);
        if (v.precioM2 < 650 && amenities <= 1) {
            return ":ParaReformar";
        }
        // Oferta: good price with decent features
        if (v.precioM2 < 1000 && amenities >= 3) {
            return ":Oferta";
        }
        if (v.precioM2 < 1200 && amenities >= 5) {
            return ":Oferta";
        }
        // Default
        return ":Vivienda";
    }

    private int countAmenities(Vivienda v) {
        int count = 0;
        if (v.tienePiscina)
            count++;
        if (v.tieneTerraza)
            count++;
        if (v.tieneParking)
            count++;
        if (v.tieneJardin)
            count++;
        if (v.aireAcondicionado)
            count++;
        if (v.cercaPlaya)
            count++;
        if (v.amueblado)
            count++;
        return count;
    }

    public boolean isReady() {
        return ready;
    }

    /**
     * Returns the trained decision tree as a human-readable string.
     * Useful for the presentation — you can show the tree structure.
     */
    public String getTreeDescription() {
        if (!ready)
            return "Model not available";
        try {
            return tree.toString();
        } catch (Exception e) {
            return "Error getting tree: " + e.getMessage();
        }
    }
}
