# OpenJDK (alpine) image
FROM openjdk:alpine

WORKDIR /

# Copy the built jar into the image
COPY out/artifacts/ /
COPY src/config.json /config.json

ENTRYPOINT [ "java", "-Dorg.slf4j.simpleLogger.defaultLogLevel=debug", "-jar", "/jloopix.jar" ]