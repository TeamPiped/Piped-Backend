FROM eclipse-temurin:17.0.6_10-jdk AS build

WORKDIR /app/

COPY . /app/

RUN --mount=type=cache,target=/root/.gradle/caches/ \
 ./gradlew shadowJar

FROM eclipse-temurin:17.0.6_10-jre

WORKDIR /app/

COPY --from=build /app/build/libs/piped-1.0-all.jar /app/piped.jar

COPY VERSION .

EXPOSE 8080

CMD java -jar /app/piped.jar
