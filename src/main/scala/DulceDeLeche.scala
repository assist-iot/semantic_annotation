package org.sripas.seaman

import control._
import rest.Api
import rml.CARMLChannelMetadata
import streaming.core.channels.ChannelManager
import utils.ConfigManager

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import ch.qos.logback.classic.LoggerContext
import org.mongodb.scala.connection.ClusterSettings
import org.mongodb.scala.{MongoClientSettings, MongoCredential, ServerAddress}
import org.slf4j.LoggerFactory

import java.util.concurrent.TimeoutException
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success}

object DulceDeLeche extends App {

  implicit val actorSystem: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "SeamanStreamer")

  // TODO: separate execution context or agent system for Streaming and for HTTP?
  val actorExecutionContext: ExecutionContext = actorSystem.executionContext
  // A separate shutdown execution context is needed to run shutdown hook independently of the processes being shut down
  implicit val shutdownExecutionContext: ExecutionContext = ExecutionContext.global

  val logger = LoggerFactory.getLogger(DulceDeLeche.getClass)

  logger.info(s"Initializing...")

  val channelManager = ChannelManager[Nothing, CARMLChannelMetadata](
    defaultKafkaSettings = ConfigManager.defaultKafkaSettings,
    defaultMaterializedMqttSettings = ConfigManager.defaultMaterializedMqttSettings,
    defaultParallelism = ConfigManager.defaultParallelism,
    defaultBufferSize = ConfigManager.defaultBufferSize
  )
  logger.info(s"Channel manager created")

  val (
    channelController: CARMLChannelController,
    annotationsController: RMLAnnotationsController,
    statusController: StatusController) = if (ConfigManager.standaloneMode) {
    val channelController = new StandaloneChannelController[Nothing](
      channelManager = channelManager,
      authCipherSecret = ConfigManager.encryptionAuthSecret
    )(actorExecutionContext)
    (
      channelController,
      new DisabledAnnotationsController(s"Annotations service is not available in standalone mode!"),
      channelController)
  } else {

    val databaseName = ConfigManager.mongoDatabase
    val mongoClientSettings = MongoClientSettings.builder()
      .applyToClusterSettings((builder: ClusterSettings.Builder) => builder.hosts(
        List(
          new ServerAddress(ConfigManager.mongoHost, ConfigManager.mongoPort)
        ).asJava))
      // TODO: Prepare better authentication, at least a strong password
      .credential(MongoCredential.createCredential(
        ConfigManager.mongoUser,
        databaseName,
        ConfigManager.mongoPassword.toCharArray))
      .build()

    val mongoDBController = new MongoDBController[Nothing](
      databaseName = databaseName,
      clientSettings = mongoClientSettings,
      channelManager = channelManager,
      restoreChannelsStopped = ConfigManager.restoreChannelsStopped,
      authCipherSecret = ConfigManager.encryptionAuthSecret
    )(actorExecutionContext)
    (mongoDBController, mongoDBController, mongoDBController)
  }

  logger.info(s"Controllers created")

  // TODO: Include Paho in logging
  // TODO: Configure sensible logfile rolling policy (by size?). Remove old log files. Enable compression?

  // TODO: Configure JVM options with sensible, but large memory limits

  // TODO: scalac: 6 deprecations (since 2.13.3); re-run with -deprecation for details
  // TODO: scalac: 3 feature warnings; re-run with -feature for details

  // TODO: Write tests - separate for RML and streamer core

  // TODO: Multiple connections from a single MQTT client (single clientID) problem - how can this be solved? Is it a problem to have many connections (Ntopics x Nchannels?) Is this a problem only for TCP?

  val api = new Api[Nothing](channelController, annotationsController, statusController)

  logger.info(s"Api instance created")

  logger.info(s"Initializing controllers")
  statusController.initialize

  val server = Http().newServerAt(ConfigManager.httpHost, ConfigManager.httpPort).bind(api.routes)

  // Intercept SIGTERM for gracious shutdown
  scala.sys.addShutdownHook {
    logger.info(s"Shutting down. Waiting ${ConfigManager.maxShutdownDuration} for all subsystems to shut down...")
    val shuttingDown = Future.sequence(List(
      // Stop channels and close HTTP server
      server.flatMap(serverBinding => {
        logger.info(s"HTTP server unbinding from ${serverBinding.localAddress} and terminating")
        val unbindFuture = serverBinding.terminate(ConfigManager.maxShutdownDuration)
        unbindFuture.onComplete {
          case Failure(exception) => logger.error(s"Error when unbinding and terminating server: $exception")
          case Success(_) => logger.info(s"Unbound and terminated HTTP server")
        }
        unbindFuture
      }), {
        logger.info(s"Stopping controllers")
        val channelsStopFuture = statusController.shutdown
        channelsStopFuture.onComplete {
          case Failure(exception) => logger.error(s"Error when stopping controllers: $exception")
          case Success(_) => logger.info(s"Controllers stopped")
        }
        channelsStopFuture
      }
    ))
      // Terminate agent system
      .map(_ => actorSystem.terminate())
      .flatMap(_ => {
        logger.info(s"Terminating agent system")
        val agentSystemTerminationFuture = actorSystem.whenTerminated
        agentSystemTerminationFuture.onComplete {
          case Failure(exception) => logger.error(s"Error when terminating agent system: $exception")
          case Success(_) => logger.info(s"Agent system terminated")
        }
        agentSystemTerminationFuture
      })
      // Shutdown logger
      .map(_ => {
        val loggerFactory = LoggerFactory.getILoggerFactory
        loggerFactory match {
          case context: LoggerContext =>
            logger.info(s"Shutting down logger")
            context.stop()
        }
      })

    Await.ready(shuttingDown, ConfigManager.maxShutdownDuration)
      .recoverWith {
        case t: TimeoutException =>
          // Logger may be already shut down, but we're in a critical situation, so let's try to log anyway
          logger.error("Timeout: Forcing shut down")
          throw new TimeoutException("Forcing shut down")
      }
  }

  // This has to be done after shutdown hook is registered, in case exit(1) is called
  server.onComplete {
    case Success(serverBinding) =>
      logger.info(s"HTTP server bound to ${serverBinding.localAddress}")
    case Failure(exception) =>
      logger.error(s"Error when binding HTTP server: $exception")
      scala.sys.exit(1)
  }(actorExecutionContext)
}
