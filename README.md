# ssii-practica-agentes

## Autores en orden alfabético

- Álvaro Smith Pérez
- Daniel Vázquez Molina
- Gauthier Vanlier
- Georgi Tsonchev Hristov
- Ignacio Sánchez Espartero

## Instrucciones de instalación

1. Clona este repositorio
2. Sigue las instrucciones de ejecución

## Captura de dependencias

Aparte de una versión Java reciente, las dependencias Java necesarias para ejecutar el proyecto con los datos prescrapeados están contenidas en agents/lib. No es necesario instalar nada más.

Para el scraper (tools/scraper), además de una versión reciente de Python 3 y pip, es necesario instalar con pip install -r requirements.txt las dependencias. También es necesario tener un navegador compatible con nodriver (basados en Chromium).

## Instrucciones de ejecución

En resumen, se trata de compilar como es usual y ejecutar los agentes también como viene siendo usual. Es necesario, eso sí, asegurarse de que los datos, modelos y ontologías sean accesibles por los agentes.

Utiliza run.sh para ejecutar el proyecto bajo Linux. Bajo otras plataformas la ejecución es idéntica hasta donde hemos podido comprobar.

Para el scraper (tools/scraper), una vez instaladas las dependencias, se ejecuta con python3 scrape_kyero.py --browser-path <BROWSER_PATH>. Utiliza python3 scrape_kyero.py --help para ver más opciones.

## Datos de ejemplo para ejecutar la práctica

Los datos prescrapeados, modelos y ontologías están incluidos en agents/src/es/upm/ssii/reagent. run.sh los gestiona para que estén accesibles para los agentes.

Los datos son generados por el scraper (tools/scraper).

## Diagrama de la arquitectura del sistema

<img alt="Diagrama de la arquitectura del sistema" src="<img width="687" height="447" alt="diagrama" src="https://github.com/user-attachments/assets/dc3af957-8e9b-4b5d-bd63-69176db1a793" />

## Declaración de IA

La IA se ha utilizado exclusivamente para tareas mecánicas, como calcular posiciones de los elementos de la UI y definir campos de datos en el módulo tools/scraper, en ontologías y modelos. Ninguna decisión arquitectural del sistema o de los agentes ha sido delegada a la IA.
