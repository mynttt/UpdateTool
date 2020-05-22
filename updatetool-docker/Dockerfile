FROM openjdk:11-jre-slim

RUN apt-get update && apt-get install -y wget
WORKDIR /usr/app
COPY docker-shared/*.sh /usr/app/
RUN /usr/app/bootstrap.sh
VOLUME /config
WORKDIR /config
ENTRYPOINT /usr/app/entry.sh
