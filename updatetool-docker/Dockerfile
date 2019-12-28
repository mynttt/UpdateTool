FROM openjdk:11

WORKDIR /usr/app
COPY *.sh /usr/app/
RUN /usr/app/bootstrap.sh
VOLUME /config
WORKDIR /config
ENTRYPOINT /usr/app/entry.sh