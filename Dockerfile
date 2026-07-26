FROM docker.io/library/eclipse-temurin:21-jdk AS builder

WORKDIR /app
COPY tax-domain ./tax-domain
COPY valuation-engine ./valuation-engine
WORKDIR /app/tax-domain
RUN apt-get update && apt-get install -y wget unzip && \
    wget -q https://services.gradle.org/distributions/gradle-8.7-bin.zip && \
    unzip -q gradle-8.7-bin.zip && \
    /app/tax-domain/gradle-8.7/bin/gradle :tax-adapter-api:jar

FROM docker.io/library/eclipse-temurin:21-jre

RUN apt-get update && apt-get install -y python3 python3-pip && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=builder /app/tax-domain/tax-adapter-api/build/libs/tax-adapter-api-1.0.0-SNAPSHOT.jar /app/app.jar
COPY parsers /app/parsers
COPY rules /app/rules
COPY web-cockpit /app/web-cockpit

RUN pip3 install --no-cache-dir -r /app/parsers/requirements.txt --break-system-packages

EXPOSE 8080
VOLUME /app/data

ENV DUCKDB_PATH=/app/data/tax_ledger.duckdb
ENV PYTHONPATH=/app/parsers/src:/app/parsers

CMD ["java", "-jar", "/app/app.jar"]
