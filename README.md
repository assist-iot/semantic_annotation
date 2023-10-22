# SemAnnStreamer_core

Semantic Annotator streaming core with CARML RML processor for Kafka and MQTT.

More detailed documentation can be found in the ASSIST-IoT ReadTheDocs and in the project Wiki.

## Installation and running

### Before installation

#### Authentication secret key

Seaman streamer encrypts passwords and usernames for storage in the database. The encryption uses a secret key, that needs to be configured in the application.conf file under seaman.encryption.auth_secret key, or through an environment variable (see the application.conf file). The key should have 32 characters. For additional security, each individual deployment should have its own secret key.

Note, that changing the secret key will prevent the decryption of previously stored usernames and passwords.

### Docker image

Build an image using the provided Dockerfile.

```
$ docker build -t seaman_core .
```

Run it and bind ports.

```
$ docker run -p [x]:[y] seaman_core
```

Ports are set in the src/main/resources/application.conf file, or via environment variables passed to docker (their names are also in application.conf). In general, only the REST port needs to be exposed for incoming connections.

### Docker compose

Use the provided docker-compose.yml file to build and run the container.

```
$ docker-compose build
$ docker-compose up
```

Environment variables can be set in the docker-compose.yml file. Shared environment variables are additionally set in the .env file. Docker-compose file also sets up a container for a persistent database. If only the standalone mode is required, the database container does not need to be run - it can be turned off, or removed from the docker-compose.yml file altogether.

Unless configured otherwise, the REST API is available on port 8488 and database GUI on port 8489. Access credentials for the database GUI are set in the .env file. By default, the database port itself is not exposed.

### Manual

#### Universal package

Thanks to the [sbt-native-packager plugin](https://www.scala-sbt.org/sbt-native-packager/index.html), the code can be packaged into a native executable.

For default settings, run `$ sbt stage`. The application can be run from target/universal/scripts/bin/[executable name]

For more packaging options, see [native packager project archetypes](https://www.scala-sbt.org/sbt-native-packager/archetypes/index.html#archetypes)

#### JAR

To package the code into a .jar file, use sbt commands.

```
$ sbt update
$ sbt compile
$ sbt package
```

The output .jar file can be found in target/scala-[version] directory and can be run with the `$ java -jar [.jar filename]` command.`.

Alternatively you can _try_ to build a far JAR using the sbt-assembly plugin, but you'll have to set and test a [merge strategy](https://github.com/sbt/sbt-assembly#merge-strategy).

```
$ sbt assembly
```

## Usage

Usage details and examples are described in the ASSIST-IoT ReadTheDocs and in the project Wiki. Quick usage instructions are:

- Use standalone mode (configured in application.conf file), or set up a MongoDB database
- Run the Streamer and open [swagger](http://localhost:8080/swagger/)
- Run a Kafka broker, or MQTT broker for the streaming core to connect to
  - Current default connection settings are available from an endpoint [swagger](http://localhost:8080/settings)
  - Default settings can be changed in application.conf file
  - Settings can be changed per-topic in every channel
- Use a GUI, such as [MQTT Explorer](https://mqtt-explorer.com/) to see the messages

## Authors and acknowledgment

szmejap

## License

Apache 2.0
