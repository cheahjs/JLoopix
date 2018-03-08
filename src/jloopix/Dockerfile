# OpenJDK (alpine) image
FROM openjdk:alpine

WORKDIR /

# Copy the built jar into the image
COPY build/libs/jloopix-standalone.jar /jloopix.jar
COPY src/config.json /config.json

ENTRYPOINT [ "java", "-Dorg.slf4j.simpleLogger.defaultLogLevel=debug", "-Dorg.slf4j.simpleLogger.showDateTime=true", "-Dorg.slf4j.simpleLogger.dateTimeFormat=yyyy-MM-dd HH:mm:ss.SSS", "-jar", "/jloopix.jar" ]