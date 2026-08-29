# PSMF Match Report agent sandbox.
#
# Adapted from the golblok-app sandbox, which is the proven setup on this
# host. The differences are Kotlin Multiplatform related: this image builds
# the shared module for JVM and Android only. iOS targets require macOS and
# are never built here.
#
# Deliberately contains NO SSH keys and NO git credential helper: the
# container must never be able to push to a remote. Signed release builds
# are produced by a human outside this workspace, so no keystore is mounted.
FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive

# Core utilities, Java 17, and Node 24.
RUN apt-get update && apt-get install -y --no-install-recommends \
        openjdk-17-jdk \
        git \
        curl \
        wget \
        unzip \
        ca-certificates \
    && curl -fsSL https://deb.nodesource.com/setup_24.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# ktlint CLI and jq, both used by the agent hooks in .claude/settings.json.
#
# The hook runs ktlint directly rather than through Gradle: `./gradlew
# ktlintFormat` costs seconds of Gradle startup on every single file edit,
# where the CLI is near-instant.
#
# KTLINT_VERSION must match `ktlint` in gradle/libs.versions.toml, so that
# the hook and the `ktlintCheck` task cannot disagree about style. Both
# read the same .editorconfig.
ARG KTLINT_VERSION=1.8.0
RUN apt-get update && apt-get install -y --no-install-recommends jq \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/* \
    && curl -sSLo /usr/local/bin/ktlint \
         https://github.com/pinterest/ktlint/releases/download/${KTLINT_VERSION}/ktlint \
    && chmod +x /usr/local/bin/ktlint

ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=$PATH:$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

# Android SDK command-line tools.
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/cmdline-tools.zip && \
    unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_HOME}/cmdline-tools && \
    rm /tmp/cmdline-tools.zip && \
    mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest

RUN yes | sdkmanager --licenses > /dev/null

# build-tools must match compileSdk. golblok learned this the hard way with
# build-tools 34.0.0 against an SDK 36 compile.
#
# Note the platform is "android-37.0", not "android-37": Android now
# versions platforms with a minor component, and sdkmanager will not
# resolve the bare major. AGP still takes `compileSdk = 37` and finds it.
#
# These two must stay in step with androidCompileSdk and androidBuildTools
# in gradle/libs.versions.toml. When they drift, AGP asks sdkmanager for
# the missing package and re-downloads it on EVERY container run, because
# `docker compose run --rm` throws the writable layer away each time.
RUN sdkmanager "platform-tools" "platforms;android-37.0" "build-tools;37.0.0"

RUN chown -R 1000:1000 ${ANDROID_HOME}

# uid/gid 1000 matches the default WSL user, so files created in the
# bind-mounted repo are owned correctly on the host side.
RUN userdel -r ubuntu 2>/dev/null || true; \
    groupadd -g 1000 developer && \
    useradd -u 1000 -g developer -m developer

# Installed globally so sessions run the claude CLI directly, never via npx.
RUN npm install -g @anthropic-ai/claude-code

# Pre-create the Gradle home. A named volume mounted at a path that does not
# exist in the image is created root-owned, which the developer user cannot
# write to; creating it here makes the volume inherit developer ownership.
RUN mkdir -p /home/developer/.gradle && chown -R developer:developer /home/developer/.gradle

# Kotlin/Native home. Created developer-owned so that if a native target is
# ever configured the toolchain has somewhere writable to land. Nothing
# downloads into it on Linux.
RUN mkdir -p /home/developer/.konan && chown -R developer:developer /home/developer/.konan

USER developer
WORKDIR /workspace
