FROM docker.io/library/debian:bookworm-slim
WORKDIR /src
RUN apt-get update && apt-get install -y \
    curl unzip maven git \
    && rm -rf /var/lib/apt/lists/*
RUN curl -sL "https://api.adoptium.net/v3/binary/latest/23/ga/linux/x64/jdk/hotspot/normal/eclipse" -o jdk.tar.gz \
    && mkdir -p /usr/lib/jvm/temurin-23 \
    && tar -C /usr/lib/jvm/temurin-23 --strip-components=1 -xzf jdk.tar.gz \
    && rm -f jdk.tar.gz
ENV JAVA_HOME="/usr/lib/jvm/temurin-23"
ENV PATH="${JAVA_HOME}/bin:${PATH}"
