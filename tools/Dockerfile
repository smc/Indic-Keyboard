FROM openjdk:8-jdk

ENV ANDROID_COMPILE_SDK: "24"
ENV ANDROID_BUILD_TOOLS: "25.0.2"
ENV ANDROID_SDK_TOOLS: "25.2.5"

RUN apt-get --quiet update --yes && \
  apt-get --quiet install --yes wget tar unzip lib32stdc++6 lib32z1 && \
  wget --quiet --output-document=android-sdk.zip https://dl.google.com/android/repository/tools_r${ANDROID_SDK_TOOLS}-linux.zip && \
  unzip android-sdk.zip && \
  echo y | tools/android --silent update sdk --no-ui --all --filter android-${ANDROID_COMPILE_SDK} && \
  echo y | tools/android --silent update sdk --no-ui --all --filter platform-tools && \
  echo y | tools/android --silent update sdk --no-ui --all --filter build-tools-${ANDROID_BUILD_TOOLS} && \
  echo y | tools/android --silent update sdk --no-ui --all --filter extra-android-m2repository && \
  echo y | tools/android --silent update sdk --no-ui --all --filter extra-google-google_play_services && \
  echo y | tools/android --silent update sdk --no-ui --all --filter extra-google-m2repository && \
  export ANDROID_HOME=$PWD/ && \
  export PATH=$PATH:$PWD/ && \
  chmod +x ./gradlew && \
