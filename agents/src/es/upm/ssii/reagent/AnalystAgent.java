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

    //esto es el comportamiento principal
    //un bucle continuo para procesar los comandos del broker
    private class HandleRequests extends CyclicBehaviour{
        //el filtro bloquante: es el Patrón de máscara para capturar solo mensajes REQUEST con la ontología correcta
        //solo acepta comunicaciones Request y solo mensajes que coincidan con la ontologia de la imobilaria
        private final MessageTemplate filter = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.REQUEST), MessageTemplate.MatchOntology(ONTOLOGY));

        //esto es la tarea ejecutada continuamente por el planificador de JADE
        @Override
        public void action(){
            //ectraemos el primer mensaje de la cola que cumple los requisitos del filtro
            ACLMessage request = myAgent.receive(filter);
            
            if(request != null){
                //extraemos la carga util del mensaje que contiene los filtros en JSON
                String filterJson = request.getContent();
                System.out.println("AnalystAgent: Request de " + request.getSender().getLocalName());
                //Invocamos la funcion intermedia para solicitar datos al informador
                List<Vivienda> viviendas = fetchViviendas(filterJson);
                //comprobamos que no este vacia
                if(viviendas == null || viviendas.isEmpty()){
                    //enviamos un mensaje estructurada vacia para no romber el broker
                    System.out.println("AnalystAgent: No hay viviendas para analizar");
                    return;
                }

                System.out.println("AnalystAgent: Analizando " viviendas.size() + " viviendas...");
                //una lista vacia para acumular los JSON resumen individuales
                JSONArray resultsArray = new JSONArray();
                //y ahora para acumular el código RDF/TTL
                StringBuilder allTTL = new StringBuilder();

                //añade las cabeceras estandar de prefijos y ontologías RDF
                allTTL.append("@prefix : <http://www.ssii.upm.es/inmobliaria#> .\n"); // Prefijo del dominio base de la práctica
                allTTL.append("@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n"); // Prefijo estándar de tipos RDF
                allTTL.append("@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n\n"); // Prefijo estándar de tipos de datos numéricos

                int ofertas = 0;
                int normales = 0;
                int reforma = 0;
                int descartados = 0;

                for(Vivienda v: viviendas){
                    try{
                        //la Prediccion con el modelo de aprendizaje automatico de Weka
                        //Obtiene la clase inicial basada en atributos numericos
                        String prediccionWeka = clasificador.clasificar(v); 

                        //ahora hacemos el refinamiento mediante PLN basado en el texto de la descripcion de la vivienda
                        //esto convierte el modelo al formato JSON dinamico requerido
                        JSONObject viviendaJson = viviendaToJSON(v);
                        //ahora procesamos el texto y generamos el fragmento RDF/TTL final
                        String ttlSnippet = procesadorTexto.procesarVivienda(viviendaJson, prediccionWeka)


                        //ahora la resolucion de la clase final (por ej el procesador de texto puede haber anulado a Weka)
                        String claseFinal = extractClassFromTTL(ttlSnippet, v.id); //extraemos la etiqueta de clase real del RDF generado
                        //concatenamos el fragmento de codigo al documento global
                        allTTL.append(ttlSnippet);
                        //ahora hacemos la clasificacion final del analisis combinado
                        if(claseFinal.contains("Oferta")){
                            ofertas++;
                        }else if(claseFinal.contains("ParaReformar")){
                            reforma++;
                        }else if(claseFinal.contains("Descartado")){
                            descartados++;
                        }else{
                            normales++;
                        }

                        //ahora hacemos la construccion de la entrada de los resultados en formato JSON para el Broker
                        JSONObject result = new JSONObject();
                        result.put("id", v.id);
                        result.put("titulo", v.titulo != null ? v.titulo : "");
                        result.put("ciudad", v.ciudad != null ? v.ciudad : "");
                        result.put("zona", v.zona != null ? v.zona : "");
                        result.put("precio", v.precio);
                        result.put("precioM2", v.precioM2);
                        result.put("superficieM2", v.superficieM2);
                        result.put("habitaciones", v.habitaciones);
                        result.put("banos", v.banos);
                        result.put("prediccionWeka", prediccionWeka);
                        result.put("claseFinal", claseFinal); //registra la clasificacion original que dio weka
                        result.put("puntuacionNLP", procesadorTexto.calcularPuntuacionNLP(v.descripcion)); //esto calcula y guarda los puntos de palabras clave

                        resultsArray.put(result);
                    }catch(Exception e){
                        System.err.println("AnalysisAgent: Error en " + v.id + ":" + e.getMessage());
                    }
                }
                System.out.println("AnalysisAgent: Acabado: %d Ofertas, %d Viviendas, %d ParaReformar, %d Descartados%n", ofertas, normales, reforma, descartados);

                JSONObject response = new JSONObject();
                response.put("resultados", resultsArray);
                response.put("total", viviendas.size());
                response.put("ofertas", ofertas);
                response.put("normales", normales);
                response.put("paraReformar", reforma);
                response.put("descartados", descartados);
                response.put("ttlCompleto", allTTL.toString());
                //Enviamos la respuesta masiva formateada directamente de vuelta al Broker
                sendReply(request, respone.toString());

            }else{
                //esto es el filtro bloqueante,que duerme el comportamiento para evitar bucles que saturen la cpu
                block();
            }
        }

        //la comunicacion intermedia con el agente informador
        private List<Vivienda> fetchViviendas(String filterJson){
            //esto busca el DF dinamicamente para localizar la direccion del informador 
            AID infoAgent = findInformationAgent();
            if(infoAgent == null){
                return new ArrayList<>(); //devolvemos una lista vacia
            }

            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.addReceiver(infoAgent);
            //esto añade un ID de conversacion usando el reloj del sistema
            msg.setConversationId("anayst-query-" + System.currentTimeMillis());
            //esto carga los criterios de filtrado JSON o texto vacio si es nulo
            msg.setContent(filterJson != null ? filterJson : "");
            send(msg);

            System.out.println("AnalystAgent: Query enviado a " + infoAgent.getLocalName()+ ", esperando por la respuesta....");
            //ahora hacemos el filtro para interceptar unicamente la respuesta relacionada con esta sesion de conversacion
            MessageTemplate replyFilter = MessageTemplate.MatchConversationId(msg.getConversationId());
            //bloqueamos el hilo y esperamos hasta 30 segundos
            ACLMessage reply = blockingReceive(replyFilter, 30000);
            //ahora en el caso de que se agota el tiempo
            if(reply == null){
                System.out.println("AnalystAgent: Timeout, esperando para los datos");
                //y devolvemos el array vacio para que no da error
                return new ArrayList<>();
            }

            try{
                //Aplicamos reflexion para definir la firma generica List<Vivienda>
                Type listType = new TypeToken<List<Vivienda>>(){}.getType();
                List<Vivienda> result = gson.fromJson(reply.getContent(), listType); //esto desearaliza la cadena de JSON
                System.out.println("AnaystAgent: Recibido " + result.size() + "propiedades");
                return result;
            }catch(Exception e){
                System.err.println("AnalystAgent: Parse error - " + e.getMessage());
                return new ArrayList<>();
            }

        }

        //los metodos helpers

        private void sendReply(ACLMessage original, String content){
            //esto genera una plantilla de respuesta
            ACLMessage reply = original.createReply();
            //esto establece la intencion del mensaje
            reply.setPerformative(ACLMessage.INFORM);
            //esto mete el sello metadato de la ontologia inmobiliaria obligatoria
            reply.setOntology(ONTOLOGY);
            //y ahora cargamos la cadena de texto con los resultados en el cuerpo del mensaje
            reply.setContent(content);
            send(reply);

        }

        private String buildEmptyResponse() { 
            JSONObject r = new JSONObject(); 
            //esto Añade una lista vacia para evitar fallos de lectura en el Broker
            r.put("resultados", new JSONArray()); 
            r.put("total", 0); 
            r.put("ofertas", 0);
            r.put("normales", 0); 
            r.put("paraReformar", 0); 
            r.put("descartados", 0); 
            return r.toString(); //esto devuelve la cadena JSON serializada de seguridad
        } 

        //este es el adaptador para traducir el modelo Gson a objetos dinámicos de org.json
        private JSONObject viviendaToJSON(Vivienda v) { 
            JSONObject j = new JSONObject(); 
            j.put("id", v.id != null ? v.id : "unknown"); 
            j.put("precio", v.precio);
            j.put("precioM2", v.precioM2); 
            j.put("superficieM2", v.superficieM2);
            j.put("ciudad", v.ciudad != null ? v.ciudad : ""); 
            j.put("zona", v.zona != null ? v.zona : ""); 
            j.put("habitaciones", v.habitaciones);
            j.put("banos", v.banos); 
            j.put("tienePiscina", v.tienePiscina); 
            j.put("tieneTerraza", v.tieneTerraza); 
            j.put("cercaPlaya", v.cercaPlaya); 
            j.put("distanciaAeropuertoKm", (double) v.distanciaAeropuertoKm); 
            j.put("descripcion", v.descripcion != null ? v.descripcion : ""); 
            return j; 
        } 
        
        //esto es el escaner de texto para extraer la clase final del bloque RDF/TTL
        private String extractClassFromTTL(String ttl, String id) { 
            //esto construye el patron que buscamos exacto de la tripleta semantica por ejemplo( ":123 rdf:type ")
            String prefix = ":" + id + " rdf:type "; 
            int start = ttl.indexOf(prefix);
            if (start < 0) return ":Vivienda"; 
            int classStart = start + prefix.length(); 
            int classEnd = ttl.indexOf(' ', classStart); 
            if (classEnd < 0) classEnd = ttl.indexOf(';', classStart)
            if (classEnd < 0) return ":Vivienda"; 
            return ttl.substring(classStart, classEnd).trim();
        } 

    }




}
