FROM clojure:lein-2.7.1-alpine

RUN mkdir -p /usr/src/election-mail-http-api
WORKDIR /usr/src/election-mail-http-api

COPY project.clj /usr/src/election-mail-http-api/

ARG env=production

RUN lein with-profile $env deps

COPY . /usr/src/election-mail-http-api

RUN lein with-profiles $env,test test
RUN lein with-profile $env uberjar

CMD ["java", "-jar", "target/election-mail-http-api.jar"]