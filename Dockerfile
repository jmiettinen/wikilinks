FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

RUN addgroup -S wikilinks && adduser -S -G wikilinks wikilinks

COPY build/libs/wikilinks.jar /app/wikilinks.jar
COPY build/wikis/ /app/wikis/
COPY docker/entrypoint.sh /app/entrypoint.sh

RUN chmod 0555 /app/entrypoint.sh && chown -R wikilinks:wikilinks /app

USER wikilinks

ENV PORT=8080
ENV JAVA_OPTS="--enable-native-access=ALL-UNNAMED -XX:MaxRAMPercentage=75.0"

EXPOSE 8080

ENTRYPOINT ["/app/entrypoint.sh"]
