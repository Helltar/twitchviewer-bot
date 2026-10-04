FROM gradle:9.0.0-jdk21-alpine AS builder

WORKDIR /app

COPY build.gradle.kts gradle.properties settings.gradle.kts ./
COPY gradle/libs.versions.toml gradle/
COPY src ./src

# the dependencies live in a cache mount rather than in a warm-up layer, so they survive a change to
# the build files, which would throw the layer away
RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle --no-daemon shadowJar

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN apk add --no-cache ffmpeg python3 && \
    apk add --no-cache --virtual .pip py3-pip && \
    pip3 install --no-cache-dir --break-system-packages --root-user-action=ignore streamlink && \
    apk del .pip

RUN adduser -u 1000 -D -s /bin/sh twitchbot
USER twitchbot

COPY --from=builder /app/build/libs/*-all.jar twitchviewer-bot.jar

HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
    CMD test $(( $(date +%s) - $(stat -c %Y /tmp/health 2>/dev/null || echo 0) )) -lt 90

ENTRYPOINT ["java", "-jar", "twitchviewer-bot.jar"]
