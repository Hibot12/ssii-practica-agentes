#!/bin/bash
cd ./agents/
mkdir -p bin
javac --release 21 -cp "lib/*" -d bin src/es/upm/ssii/reagent/*.java
cp src/es/upm/ssii/reagent/*.arff src/es/upm/ssii/reagent/*.json src/es/upm/ssii/reagent/*.ttl bin/es/upm/ssii/reagent/
java -cp "lib/*:bin" jade.Boot -gui -agents "analyst:es.upm.ssii.reagent.AnalystAgent;broker:es.upm.ssii.reagent.BrokerAgent;info:es.upm.ssii.reagent.InformationSourcingAgent;ui:es.upm.ssii.reagent.PresentationUIAgent"
rm -r bin
cd ..
