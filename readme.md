# Java Method FQN Resolver

This tool is modified from https://github.com/mhilmiasyrofi/ausearch in order to resolve type of method calls in a Java file when giving Java file and need jars as inputs.


## Prerequisite

- [Java Development Kit (JDK)](https://www.oracle.com/technetwork/java/javase/downloads/index.html), version 1.8.
- [Apache Maven](https://maven.apache.org/), version 3.0 or later.

## Getting Started

- Needed jars should be placed at `src/main/java/com/project/methodfqnresolver/jars/`. Although the tool tries to download maven packages from import statement, it would be better if needed jars are prepared.
- Java files to be resolved should be placed at `src/main/java/com/project/methodfqnresolver/testCodes/`.
- Output files would be available at `src/main/java//com/project/methodfqnresolver/outputs/`

## How to Run

```
// Create docker container
docker-compose build

// Get into the container
docker exec -it java-method-resolver bash

[In the container]
// go to your project directory
cd /app/JavaMethodFQNResolver

// compile project with maven
mvn clean compile assembly:single

// run the jar 
java -cp target/method-fqn-resolver-1.0-SNAPSHOT-jar-with-dependencies.jar com.project.methodfqnresolver.App
```