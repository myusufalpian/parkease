# syntax=docker/dockerfile:1.7@sha256:a57df69d0ea827fb7266491f2813635de6f17269be881f696fbfdf2d83dda33e

FROM eclipse-temurin:25-jdk@sha256:e787e08ef76f4c16866108cd7f9fcd96a68eef3ac6cc76866897d4d02d5a2262 AS builder

WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon --console=plain
RUN mkdir -p /workspace/layers \
    && java -Djarmode=tools -jar /workspace/build/libs/*.jar extract --layers --launcher \
       --destination /workspace/layers \
    && jdeps --ignore-missing-deps --multi-release 25 --print-module-deps \
       --class-path '/workspace/layers/dependencies/*:/workspace/layers/snapshot-dependencies/*' \
       /workspace/layers/application/BOOT-INF/classes > /workspace/jre-modules.txt \
    && jlink --add-modules "$(cat /workspace/jre-modules.txt),jdk.crypto.ec" \
       --strip-debug --no-man-pages --no-header-files --compress=2 \
       --output /workspace/jre

FROM debian:bookworm-slim@sha256:88200866dfff7ea7f5cbcb6ec7c8a701889efe6fe859fe64d6990e4b07ea4171 AS runtime

RUN groupadd --system --gid 10001 parkease \
    && useradd --system --uid 10001 --gid 10001 --create-home --home-dir /nonexistent --shell /usr/sbin/nologin parkease \
    && apt-get update \
    && apt-get install --no-install-recommends --yes wget \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=builder /workspace/jre /opt/java
COPY --from=builder /workspace/layers/dependencies /app/lib
COPY --from=builder /workspace/layers/snapshot-dependencies /app/lib
COPY --from=builder /workspace/layers/application/BOOT-INF/classes /app/classes
COPY --from=builder /workspace/layers/application/META-INF /app/META-INF

USER parkease
EXPOSE 8080
ENTRYPOINT ["/opt/java/bin/java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75", "-cp", "/app/classes:/app/lib/*", "id.xyz.parkease.ParkeaseApplication"]
