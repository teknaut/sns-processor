#!/usr/bin/env bash
#
# build.sh — Build the native Lambda binary inside an Amazon Linux 2023 container.
#
# Requirements:
#   - Docker (running, with linux/amd64 support)
#   - Internet access to download Maven and GraalVM CE on first run
#
# Output:
#   target/bootstrap  — native executable
#   target/function.zip — deployment artifact for Serverless Framework

set -euo pipefail

MAVEN_VERSION="3.9.9"
# GraalVM Community Edition 25 — update the tag/URL when a new patch is released.
# Check https://github.com/graalvm/graalvm-ce-builds/releases for the latest.
GRAALVM_VERSION="25.0.0"
GRAALVM_ARCHIVE="graalvm-community-jdk-${GRAALVM_VERSION}_linux-x64_bin.tar.gz"
GRAALVM_URL="https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-${GRAALVM_VERSION}/${GRAALVM_ARCHIVE}"

echo "==> Building s3-sns-processor (native image for provided.al2023)"
echo "    GraalVM CE : ${GRAALVM_VERSION}"
echo "    Maven      : ${MAVEN_VERSION}"
echo ""

docker run --rm \
  --platform linux/amd64 \
  -v "$(pwd)":/build \
  -w /build \
  amazonlinux:2023 \
  bash -c "
    set -euo pipefail

    echo '--- Installing build dependencies ---'
    dnf install -y gcc glibc-devel zlib-devel zip tar gzip curl findutils --quiet

    echo '--- Installing Maven ${MAVEN_VERSION} ---'
    curl -fsSL https://downloads.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
      | tar xz -C /opt
    export PATH=/opt/apache-maven-${MAVEN_VERSION}/bin:\$PATH

    echo '--- Installing GraalVM CE ${GRAALVM_VERSION} ---'
    curl -fsSL ${GRAALVM_URL} | tar xz -C /opt
    export JAVA_HOME=\$(ls -d /opt/graalvm-community-openjdk-${GRAALVM_VERSION}*)
    export PATH=\${JAVA_HOME}/bin:\$PATH

    echo '--- GraalVM version ---'
    java -version
    native-image --version

    echo '--- Running Maven build (native profile) ---'
    mvn package -Pnative -DskipTests --no-transfer-progress

    echo ''
    echo '==> Build complete'
    echo '    Artifact : target/function.zip'
    ls -lh target/function.zip
  "
