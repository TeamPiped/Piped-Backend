FROM eclipse-temurin:17.0.7_7-jdk AS build

WORKDIR /app/

COPY . /app/

RUN --mount=type=cache,target=/root/.gradle/caches/ \
 ./gradlew shadowJar

FROM eclipse-temurin:17.0.7_7-jre

WORKDIR /app/

COPY --from=build /app/build/libs/piped-1.0-all.jar /app/piped.jar

COPY VERSION .

EXPOSE 8080

CMD java -server -Xmx1G -XX:+UnlockExperimentalVMOptions -XX:+OptimizeStringConcat -XX:+UseStringDeduplication -XX:+UseCompressedOops -XX:+UseNUMA -XX:+UseG1GC -Xshare:on --enable-preview -jar /app/piped.jar
