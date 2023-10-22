ARG builder_jdk=eclipse-temurin-17.0.4
ARG sbt_version=1.7.2
ARG scala_version=2.13.10

#--- Builder image
FROM sbtscala/scala-sbt:${builder_jdk}_${sbt_version}_${scala_version} AS builder

# Copy sbt configuration and prepare sbt dependencies
WORKDIR /seaman_build
COPY project project
COPY build.sbt .
RUN sbt update

# Copy project files, compile and package
COPY src src
RUN sbt stage

#--- Runner image
FROM amazoncorretto:17.0.4 AS vessel

WORKDIR /seamanstreamer_core
COPY --from=builder /seaman_build/target/universal/stage/. .

RUN mv bin/$(ls bin | grep -v .bat) bin/start
CMD ["./bin/start"]
EXPOSE 8080