# Builds Wormhole X-Treme for the OSS Scanner (https://github.com/anthropics/oss-scanner).
# The scanner puts the checkout in /src, builds this with network access, then audits offline,
# so every Maven dependency has to be fetched here.
FROM maven:3.9-eclipse-temurin-17

COPY . /src
WORKDIR /src

# The jar the scanner audits. Tests are skipped here so a failing test cannot fail the image.
RUN mvn --batch-mode -DskipTests package

# Run the unit tests once so their dependencies are cached for the offline audit, but do not let
# a failure abort the build.
RUN (mvn --batch-mode test || echo "WARNING: tests reported failures")
