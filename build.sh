#!/usr/bin/env bash
#
# build.sh — Build the native Lambda binary inside an Amazon Linux 2023 container.
#
# Requirements:
#   - Docker (running, with linux/amd64 support)
#   - Internet access to download Maven and GraalVM CE on first run
#
# Output:
#   target/bootstrap    — native executable
#   target/function.zip — deployment artifact for Serverless Framework

set -euo pipefail

MAVEN_VERSION="3.9.12"
# GraalVM Community Edition 25
# Check https://github.com/graalvm/graalvm-ce-builds/releases for the latest tag.
GRAALVM_VERSION="25.0.0"
GRAALVM_URL="https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-${GRAALVM_VERSION}/graalvm-community-jdk-${GRAALVM_VERSION}_linux-x64_bin.tar.gz"

echo "==> Building s3-sns-processor (native image for provided.al2023)"
echo "    GraalVM CE : ${GRAALVM_VERSION}"
echo "    Maven      : ${MAVEN_VERSION}"
echo ""

# Write the inner build script to a temp file to avoid quoting/escaping issues
INNER_SCRIPT=$(mktemp /tmp/docker-build-XXXXXX.sh)
trap 'rm -f "$INNER_SCRIPT"' EXIT

cat > "$INNER_SCRIPT" << 'EOF'
#!/usr/bin/env bash
set -euo pipefail

echo "--- Installing build dependencies ---"
dnf install -y --allowerasing gcc glibc-devel zlib-devel zip tar gzip curl findutils --quiet

echo "--- Installing Maven ${MAVEN_VERSION} ---"
curl -fsSL "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
  | tar xz -C /opt
export PATH="/opt/apache-maven-${MAVEN_VERSION}/bin:${PATH}"

echo "--- Installing GraalVM CE ${GRAALVM_VERSION} ---"
curl -fsSL "${GRAALVM_URL}" | tar xz -C /opt

JAVA_HOME=$(ls -d /opt/graalvm-community-openjdk-* 2>/dev/null | head -1)
if [ -z "${JAVA_HOME}" ]; then
  echo "ERROR: GraalVM extraction failed. Contents of /opt:"
  ls /opt/
  exit 1
fi
export JAVA_HOME
export PATH="${JAVA_HOME}/bin:${PATH}"
echo "JAVA_HOME=${JAVA_HOME}"

echo "--- GraalVM version ---"
java -version
native-image --version

echo "--- Running Maven build (native profile) ---"
mvn package -Pnative -DskipTests --no-transfer-progress

echo ""
echo "==> Build complete"
echo "    Artifact : target/function.zip"
ls -lh target/function.zip
EOF

docker run --rm \
  --platform linux/amd64 \
  -v "$(pwd)":/build \
  -v "${INNER_SCRIPT}:/docker-build.sh" \
  -w /build \
  -e MAVEN_VERSION="${MAVEN_VERSION}" \
  -e GRAALVM_VERSION="${GRAALVM_VERSION}" \
  -e GRAALVM_URL="${GRAALVM_URL}" \
  amazonlinux:2023 \
  bash /docker-build.sh
