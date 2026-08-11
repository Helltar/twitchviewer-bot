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

# the app refreshes /tmp/health only while its getUpdates loop keeps cycling, so a stale file means
# the bot stopped polling telegram — which a process-level check cannot see, since polling runs on a
# scheduled executor the jvm happily outlives
#
# `test` rather than `[ … ]`: a CMD starting with `[` is read as the JSON exec form first, and only
# falls back to a shell command once that fails to parse
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
    CMD test $(( $(date +%s) - $(stat -c %Y /tmp/health 2>/dev/null || echo 0) )) -lt 90

ENTRYPOINT ["java", "-jar", "twitchviewer-bot.jar"]
