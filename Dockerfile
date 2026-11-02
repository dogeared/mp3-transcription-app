FROM eclipse-temurin:21-jammy
VOLUME /tmp
ADD target/*.jar app.jar
VOLUME /uploads
ENTRYPOINT ["java","-jar","/app.jar"]
