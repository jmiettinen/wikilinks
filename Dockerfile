FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

RUN addgroup -S wikilinks && adduser -S -G wikilinks wikilinks

COPY build/libs/wikilinks.jar /app/wikilinks.jar
COPY build/wikis/ /app/wikis/
COPY jvm.options /app/jvm.options
COPY docker/entrypoint.sh /app/entrypoint.sh

RUN chmod 0555 /app/entrypoint.sh && chown -R wikilinks:wikilinks /app

USER wikilinks

ENV PORT=8080
ENV MAX_IN_FLIGHT_ROUTES=1

EXPOSE 8080

ENTRYPOINT ["/app/entrypoint.sh"]
