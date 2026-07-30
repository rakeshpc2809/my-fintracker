FROM docker.io/library/eclipse-temurin:21-jre

# 1. Pre-install Python & dependencies (Cached OS layer)
RUN apt-get update && apt-get install -y python3 python3-pip && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# 2. Cache Python pip dependencies
COPY parsers/requirements.txt /app/parsers/requirements.txt
RUN pip3 install --no-cache-dir -r /app/parsers/requirements.txt --break-system-packages

# 3. Copy application JAR and source directories
COPY tax-domain/tax-adapter-api/build/libs/tax-adapter-api-1.0.0-SNAPSHOT.jar /app/app.jar
COPY parsers /app/parsers
COPY rules /app/rules
COPY web-cockpit /app/web-cockpit

EXPOSE 8080
VOLUME /app/data

ENV DUCKDB_PATH=/app/data/tax_ledger.duckdb
ENV PYTHONPATH=/app/parsers/src:/app/parsers

CMD ["java", "-jar", "/app/app.jar"]
