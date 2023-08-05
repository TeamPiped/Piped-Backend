FROM eclipse-temurin:17-jdk AS build

WORKDIR /app/

COPY . /app/

RUN --mount=type=cache,target=/root/.gradle/caches/ \
 ./gradlew shadowJar

FROM eclipse-temurin:17-jre

WORKDIR /app/

COPY hotspot-entrypoint.sh /

COPY --from=build /app/build/libs/piped-1.0-all.jar /app/piped.jar

COPY VERSION .

EXPOSE 8080

ENTRYPOINT ["/hotspot-entrypoint.sh"]
