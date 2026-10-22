FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /build
COPY apps/backend/pom.xml ./pom.xml
COPY apps/backend/src ./src
COPY contracts /contracts
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine@sha256:51ab5e3302e7141ce665ca3ea85e8b5cd648eafbc3c0c90dd79d6537684e4555
RUN apk upgrade --no-cache \
    && apk del fontconfig ttf-dejavu gnupg coreutils
RUN addgroup -S -g 10001 workbench && adduser -S -D -u 10001 -G workbench workbench \
    && mkdir -p /data/uploads && chown -R workbench:workbench /data
WORKDIR /app
COPY --from=build /build/target/prd-control-plane-0.5.0-beta.1.jar /app/workbench.jar
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/workbench.jar"]
