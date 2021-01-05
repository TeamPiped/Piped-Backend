FROM adoptopenjdk/openjdk11:jdk11u-ubi-nightly-slim AS build

WORKDIR /app/

COPY . /app/

RUN chmod +x ./gradlew && ./gradlew shadowJar

FROM adoptopenjdk/openjdk11:ubi-minimal-jre

WORKDIR /app/

COPY --from=build /app/build/libs/piped-1.0-all.jar /app/piped.jar

EXPOSE 8080

CMD java -jar /app/piped.jar
