FROM adoptopenjdk:11-jre-openj9
RUN mkdir /opt/app
COPY target/idaas-connect-clinical-industrystds-1.5.0-SNAPSHOT.jar /opt/app/japp.jar
EXPOSE 10001 8080 8090 10301 10801
CMD ["java", "-jar", "/opt/app/japp.jar"]