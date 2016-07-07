FROM java:8

MAINTAINER EMC

ADD build/dist/mongoose*.tgz /opt/

RUN ln -s /opt/mongoose* /opt/mongoose

EXPOSE 1099 8080 9020 9021 9022 9023 9024 9025

ENTRYPOINT ["java", "-jar", "/opt/mongoose/mongoose.jar"]
