#
# Unified Dockerfile for Hackaton service (GraalVM Native Image + Distroless)
#
# Features:
# - Multi-stage сборка: GraalVM native compilation + Distroless cc runtime
# - Пропуск тестов при сборке (по умолчанию)
# - Кэширование зависимостей Gradle
# - PGO-оптимизации (опционально, через сборочные аргументы)
#
# Требования:
# - Docker 20.10+
# - Доступно ≥ 8 GB RAM для native-сборки
#
# Использование:
#   docker build -t hackaton .
#
# С PGO (двухпроходная сборка):
#   # Шаг 1 — инструментированная сборка
#   docker build --build-arg PGO_MODE=instrument -t hackaton-instrumented .
#   # Шаг 2 — сбор профиля запуском контейнера с тестовой нагрузкой
#   # Шаг 3 — финальная сборка с профилем
#   docker build --build-arg PGO_MODE=optimized \
#     --secret id=default_iprof,src=default.iprof -t hackaton-optimized .
#
# Переменные сборки:
#   PGO_MODE      — режим PGO: 'instrument' | 'optimized' (по умолчанию пусто — без PGO)
#   SKIP_TESTS    — 'true' для пропуска тестов (по умолчанию 'true')
#
ARG BUILD_HOME=/build

#
# Stage 1: GraalVM Native Image compilation
#
FROM ghcr.io/graalvm/native-image-community:25 AS build-image

ARG BUILD_HOME
ARG PGO_MODE
ARG SKIP_TESTS=true
ENV APP_HOME=$BUILD_HOME
WORKDIR $APP_HOME

#
# Copy only build files first to cache dependencies
#
COPY gradle $APP_HOME/gradle/
COPY gradlew $APP_HOME/
COPY build.gradle settings.gradle $APP_HOME/
RUN ./gradlew --no-daemon --version

COPY settings.gradle build.gradle $APP_HOME/
RUN ./gradlew --no-daemon dependencies || true

#
# Build the native image
#
COPY . $APP_HOME/

RUN --mount=type=secret,id=default_iprof \
    export NATIVE_BUILD_ARGS=""; \
    export TEST_ARGS=""; \
    if [ "${PGO_MODE}" = "instrument" ]; then \
        export NATIVE_BUILD_ARGS="-Pbootstrap"; \
    elif [ "${PGO_MODE}" = "optimized" ] && [ -f /run/secrets/default_iprof ]; then \
        mkdir -p /tmp/pgo; \
        cp /run/secrets/default_iprof /tmp/pgo/default.iprof; \
        export NATIVE_BUILD_ARGS="-Ppgo=/tmp/pgo/default.iprof"; \
    fi; \
    if [ "${SKIP_TESTS}" != "true" ]; then \
        TEST_ARGS="--no-tests"; \
    fi; \
    ./gradlew --no-daemon nativeCompile ${NATIVE_BUILD_ARGS} ${TEST_ARGS}

#
# Stage 2: Distroless base runtime
#
FROM gcr.io/distroless/base-debian13:nonroot

ARG BUILD_HOME
ENV APP_HOME=$BUILD_HOME

#
# Copy the native executable
#
COPY --from=build-image $APP_HOME/hackaton/build/native/nativeCompile/hackaton /app

#
# The command to run when the container starts.
# The native image is a standalone executable — no JVM needed.
#
CMD ["/app"]
