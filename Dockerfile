# ── Stage 1 : compilation native ──────────────────────────────────────────────
FROM ghcr.io/graalvm/native-image-community:25 AS builder

WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -q

COPY src ./src
RUN ./mvnw -Pnative native:compile -DskipTests --no-transfer-progress

# ── Stage 2 : image finale ultra-légère ───────────────────────────────────────
FROM debian:bookworm-slim

WORKDIR /app
COPY --from=builder /app/target/VolleyMatch .

EXPOSE 8080
ENTRYPOINT ["./VolleyMatch", "--spring.profiles.active=prod"]