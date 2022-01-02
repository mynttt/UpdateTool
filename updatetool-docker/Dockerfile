FROM openjdk:11-jre-slim

RUN apt-get update && apt-get install -y wget binutils xz-utils tree
WORKDIR /usr/app
COPY docker-shared/*.sh /usr/app/
RUN /usr/app/download.sh
RUN /usr/app/plexSqliteBinaries.sh
VOLUME /config
WORKDIR /config
ENTRYPOINT /usr/app/entry.sh
