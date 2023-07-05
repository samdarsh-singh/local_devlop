# ----------------
# APP-BUILD IMAGE:
# ----------------

FROM ardoq/leiningen-jdk:11.0.16.1_1-2.9.4 AS builder
RUN apt update && apt upgrade -y && apt install -y git npm chromium-browser && apt clean
RUN lein upgrade

WORKDIR /build/rems

COPY package.json .
RUN npm install

COPY project.clj .
RUN lein install

COPY . .
RUN npx shadow-cljs compile app && lein uberjar


# ------------------
# APP-RUNTIME IMAGE:
# ------------------

FROM eclipse-temurin:17-jre
RUN chmod 664 /opt/java/openjdk/lib/security/cacerts
WORKDIR /rems

COPY --from=builder --chmod=400 /build/rems/empty-config.edn /rems/config/config.edn
COPY --from=builder --chmod=400 /build/rems/example-theme/extra-styles.css /rems/example-theme/extra-styles.css
COPY --from=builder --chmod=400 /build/rems/target/uberjar/rems.jar /rems/rems.jar
COPY --from=builder --chmod=500 /build/rems/docker-entrypoint.sh /rems/docker-entrypoint.sh

ENTRYPOINT ["/rems/docker-entrypoint.sh"]
