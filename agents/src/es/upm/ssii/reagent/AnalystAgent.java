package es.upm.ssii.reagent;
//importaciones de jade
import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

//importaciones de la libreria de google para la conversion de objetos a JSON
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

//para manipular archivos JSON de forma dinamica
import org.json.JSONArray;
import org.json.JSONObject;


import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;


//Analyst Agent - La inteligencia core del sistema multi-agente
//tenemos 
public class AnalystAgent  extends Agent{

    //constantes para identificar el tipo de ontologia, servicio y las rutas de archivos
    //el tipo de servicio para el DF
    public static final String SERVICE_TYPE = "analisis-imobilario";
    //el nombre de la ontologia del menasaje
    public static final String ONTOLOGY = "ontologia-imobilaria";
    //el identitificador del servicio del informador a n¡buscar
    private static final String INFO_SERVICE = "information_sourcing"; 
    //la ruta del dataset de entrenamiento weka
    private static final String ARRF_PATH = "data/viviendas_training.arff"
    
    private ClasificadorWeka clasificador;
    //creamos una instancia estatica GSON para reutilizarla en el parseo de JSON
    private static final Gson gson = new Gson();
    //la variable de referencia para el procesador PLN
    private ProcesadorTexto ProcesadorTexto


   //ahora el ciclo de vida del agente
   @Override
   protected void setup(){
        System.out.println("AnalystAgent " + getLocalName() + ": Iniciando...");
        //creamos el objeto de procesamiento de lenguaje natural
        procesadorTexto = new ProcesadorTexto();
        //pasamos el path al clasificador de weka
        clasificador = new ClasificadorWeka(ARFF_PATH);
        //verificamos que el modelo se entreno con exito
        if(clasificador.isReady()){
            System.out.println("AnalystAgent: Weka J48 model trained OK");
        }else{//por si fallan
            System.out.println("AnalystAgent: Weka no esta disponible");
        }
        //llamamos el metodo interno para anunciar las capacidades del agente en el DF
        registerDF();
        //Añadimos y activamos el comportamiento principal para procesar peticiones
        addBehaviour(new HandleRequests());
        System.out.println("AnalystAgent " + getLocalName() + ": Listo")
   }

   //el metodo para quitar el agente
   @Override
   protected void takeDown(){
    try{
        DFService.deregister(this);
    }catch(FIPAException ignored){
        System.out.println("AnalysisAgent " + getLocalName + ": Eliminado");
    }
   }

   //metodo de registro en el facilitador de directorio
   protected void registerDF(){
    //descripcion del agente
    DFAgentDescription dfd = new DFAgentDescription();
    //conectamos la id unica del agente AID, a la descripcion del DF
    dfd.setName(getAID());
    ServiceDescription sd = new ServiceDescription() //esto detalla el servicio concreto ofrecido por el agente
    sd.setType(SERVICE_TYPE);
    sd.setName(getLocalName);
    //añadimos el servicio concreto al contendor de descripcion de el agente
    dfd.addServices(sd);
    try{ //bloque de seguridad
        //Publicamos la desc del agente a las paginas amarillas de JADE
        DFService.register(this. dfd);
        System.out.println("AnalystAgent: Registrado en el DF '" + SERVICE_TYPE + "'")
    }catch(FIPAException e){
        System.err.println("AnaystAgent: DF error: "+ e.getMessage());
    }
   }

   //buscador dinamico del agente de datos
   private AID findInformationAgent(){
    DFAgentDescription template = new DFAgentDescription();
    ServiceDescription sd = new ServiceDescription();
    sd.setType(INFO_SERVICE);
    //añadimos el filtro de servicio a la plantilla de de busqueda general
    template.addServices(sd)
    try{
        //realiza la busqueda en el DF de JADe
        DFAgentDescription[] results = DFService.search(this, template)
        if(results.length > 0){
            //extrae el identificador AID del primer proveedor disponible
            results[0].getName();
        }
    }catch(FIPAException e){
        Sytem.err.println("AnalystAgent: DF error de busqueda: "+ e.getMessage());
    }
    System.out.println("AnalystAgent: InformationSourcingAgent no encontrado en el DF");
    return null;
   } 

}
