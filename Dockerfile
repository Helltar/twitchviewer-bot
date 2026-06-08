FROM gradle:9.0.0-jdk21-alpine AS builder

WORKDIR /app

COPY build.gradle.kts gradle.properties settings.gradle.kts ./
COPY gradle/libs.versions.toml ./gradle/
RUN gradle shadowJar -x test --no-daemon
COPY src ./src
RUN gradle shadowJar --no-daemon && \
    cp build/libs/*-all.jar twitchviewer-bot.jar

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN apk add --no-cache ffmpeg py3-pip && \
    pip3 install --no-cache-dir --break-system-packages --root-user-action=ignore streamlink && \
    adduser -u 10001 -D -s /bin/sh twitchbot

COPY --from=builder --chown=twitchbot:twitchbot /app/twitchviewer-bot.jar ./twitchviewer-bot.jar
USER twitchbot

ENTRYPOINT ["java", "-jar", "twitchviewer-bot.jar"]
