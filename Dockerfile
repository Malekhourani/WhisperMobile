FROM eclipse-temurin:17-jdk-jammy

ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=${ANDROID_HOME}
ENV PATH=${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools

# Install dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl unzip git cmake ninja-build \
    && rm -rf /var/lib/apt/lists/*

# Install Android SDK command-line tools
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    curl -fsSL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o /tmp/cmdline-tools.zip && \
    unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_HOME}/cmdline-tools && \
    mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip

# Accept licenses and install SDK components
RUN yes | sdkmanager --licenses > /dev/null 2>&1 && \
    sdkmanager \
    "platforms;android-34" \
    "build-tools;34.0.0" \
    "ndk;26.1.10909125" \
    "cmake;3.22.1"

ENV ANDROID_NDK_HOME=${ANDROID_HOME}/ndk/26.1.10909125

WORKDIR /app
COPY . .

# Ensure gradlew is executable
RUN chmod +x gradlew

# Build the release APK
RUN ./gradlew assembleRelease --no-daemon --stacktrace

# The APK will be at app/build/outputs/apk/release/app-release-unsigned.apk
