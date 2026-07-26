FROM docker.io/library/eclipse-temurin:21-jdk AS builder

# 1. Pre-install build tools & Gradle once (Cached layer)
RUN apt-get update && apt-get install -y wget unzip && \
    wget -q https://services.gradle.org/distributions/gradle-8.7-bin.zip -O /tmp/gradle.zip && \
    unzip -q /tmp/gradle.zip -d /opt && \
    rm /tmp/gradle.zip

WORKDIR /app

# 2. Copy source code and build jar
COPY tax-domain ./tax-domain
COPY valuation-engine ./valuation-engine
WORKDIR /app/tax-domain
RUN /opt/gradle-8.7/bin/gradle :tax-adapter-api:jar --no-daemon

FROM docker.io/library/eclipse-temurin:21-jre

# 3. Pre-install Python & dependencies (Cached layer)
RUN apt-get update && apt-get install -y python3 python3-pip && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# 4. Cache Python pip dependencies before copying full parser source
COPY parsers/requirements.txt /app/parsers/requirements.txt
RUN pip3 install --no-cache-dir -r /app/parsers/requirements.txt --break-system-packages

# 5. Copy built JAR and application source code
COPY --from=builder /app/tax-domain/tax-adapter-api/build/libs/tax-adapter-api-1.0.0-SNAPSHOT.jar /app/app.jar
COPY parsers /app/parsers
COPY rules /app/rules
COPY web-cockpit /app/web-cockpit

EXPOSE 8080
VOLUME /app/data

ENV DUCKDB_PATH=/app/data/tax_ledger.duckdb
ENV PYTHONPATH=/app/parsers/src:/app/parsers

CMD ["java", "-jar", "/app/app.jar"]
