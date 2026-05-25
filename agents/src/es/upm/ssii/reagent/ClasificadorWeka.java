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

//esto es el clasificador de weka
public class ClasificadorWeka {
    //declaramos el modelo del arbol de decision
    private J48 tree;
    private Instances datasetStructure; //esto guarda la estructura de datos
    //ahora creamos un flag, para saber si el modelo esta listo
    private boolean ready = false;

    //la lista de constantes, de los possibles valores de la clasificacion
    private static final List<String> CLASSES = Arrays.asList(
            "Oferta", "Vivienda", "ParaReformar", "Descartado");
    //esto es el metodo constructor que recibe el archivo de entrenamiento
    public ClasificadorWeka(InputStream arffStream) {
        try {
            //cargamos la fuente de datos desde el flujo
            DataSource source = new DataSource(arffStream);
            //extraemos el conjunto de datos completo
            Instances data = source.getDataSet();

            //ahora establecemos el ultimo atributo como la clase a predecir
            data.setClassIndex(data.numAttributes() - 1);

            //inicializamos el arbol
            tree = new J48();
            //incializamos el arbol para que usa pruning y que no haya oferfitting en el arbol
            tree.setUnpruned(false);
            //setteamos la confianca a 25% porque es el estandar y para que hay pruning para que no crea ramas para cosas especificas
            tree.setConfidenceFactor(0.25f);
            //ahora setteamos el numero min de objetos por hoja del arbol a 2,
            //solo deberia crear una regla en el arbol, solo en el caso de que esa regla aplica a 2 objetos como minimo
            tree.setMinNumObj(2);
            tree.buildClassifier(data);//entrenamos el modelo con los datos


            //guardamos solo las cabeceras
            datasetStructure = new Instances(data, 0);
            ready = true; //lo ponemos a true para que se puede utilizar el modelo

            //esto registra si se entreno con exito o no
            AgentsLogger.info("ClasificadorWeka", "Model trained successfully.");
            //el resumen del arbol
            AgentsLogger.info("ClasificadorWeka", tree.toSummaryString());

        } catch (Exception e) {
            //en el caso que no se entreno con exito
            AgentsLogger.severe("ClasificadorWeka", "Failed to train model: " + e.getMessage());
            AgentsLogger.severe("ClasificadorWeka", "Will fall back to rule-based classification.");
            ready = false;
        }
    }

   //esto es el metodo para clasificar una sola vivienda
    public String clasificar(Vivienda v) {
        if (!ready) {
            //si no se puede utilizar el model utizamos el metodo manual basado en reglas
            return clasificarFallback(v);
        }

        try {
            //convertimos el objeto vivienda al formato de weka
            Instance instance = buildInstance(v);
            //obtenemos el indice numerico de su prediccion
            double prediction = tree.classifyInstance(instance);
            //traducimos el nombre de la clase
            String className = datasetStructure.classAttribute().value((int) prediction);
            return ":" + className; //retronamos el nombre con el prefijo de ontologia
        } catch (Exception e) {
            System.err.println("ClasificadorWeka:  Error de clasificacion: " + e.getMessage());
            return clasificarFallback(v);
        }
    }

    //ahora creamos la clase para obtener las probabilidades de cada clase
    public double[] distribucion(Vivienda v) {
        if (!ready) {
            //devolvemos probabilidades equitativas para cada uno
            return new double[] { 0.25, 0.25, 0.25, 0.25 };
        }
        try {
            //intentamos calcular la distribucion
            //convertimos al formato de weka
            Instance instance = buildInstance(v);
            //Retornamos el array de probabilidades del árbol
            return tree.distributionForInstance(instance);
        } catch (Exception e) {
            return new double[] { 0.25, 0.25, 0.25, 0.25 };
        }
    }

    //este metodo sirve para transformar vivienda a instancia de weka
    private Instance buildInstance(Vivienda v) {
        //creamos la instancia vacia con el tamaño correcto
        Instance inst = new DenseInstance(datasetStructure.numAttributes());
        inst.setDataset(datasetStructure);

        inst.setValue(0, v.precioM2);
        inst.setValue(1, v.superficieM2);
        inst.setValue(2, v.habitaciones);
        inst.setValue(3, v.banos);
        inst.setValue(4, v.tienePiscina ? "true" : "false");
        inst.setValue(5, v.tieneTerraza ? "true" : "false");
        inst.setValue(6, v.tieneParking ? "true" : "false");
        inst.setValue(7, v.tieneJardin ? "true" : "false");
        inst.setValue(8, v.aireAcondicionado ? "true" : "false");
        inst.setValue(9, v.cercaPlaya ? "true" : "false");
        inst.setValue(10, v.distanciaAeropuertoKm);
        return inst;
    }

    //esto es el clasificador manual de fallback, por tener algo aunque no tenga mucho sentido pero bueno
    //durante la presentacion se utiliza el model no el fallback
    private String clasificarFallback(Vivienda v) {
        //las Descartadamos si son muy pequeñas o super caras:
        if (v.superficieM2 < 35 || v.precioM2 > 4000) {
            return ":Descartado";
        }
        //ParaReformar: si es muy barata y sin apenas extras entonces deberiamos reformarla
        int amenities = countAmenities(v);
        if (v.precioM2 < 650 && amenities <= 1) {
            return ":ParaReformar";
        }
        //Si el precio es bueno y tiene bastantes extras
        if (v.precioM2 < 1000 && amenities >= 3) {
            return ":Oferta";
        }
        if (v.precioM2 < 1200 && amenities >= 5) {
            return ":Oferta";
        }
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

    //este es el metodo para obtener el arbol en formato texto
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
