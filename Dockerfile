FROM quay.io/democracyworks/didor:latest

RUN mkdir -p /usr/src/election-mail-http-api
WORKDIR /usr/src/election-mail-http-api

COPY project.clj /usr/src/election-mail-http-api/

RUN lein deps

COPY . /usr/src/election-mail-http-api

RUN lein test
RUN lein immutant war --name election-mail-http-api --destination target --nrepl-port=11111 --nrepl-start --nrepl-host=0.0.0.0
