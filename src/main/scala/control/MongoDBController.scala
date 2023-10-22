package org.sripas.seaman
package control

import rml.exceptions.NoSuchAnnotationException
import rml.json.{MaterializedCARMLChannelDeserializer, RMLJsonSupport}
import rml.{CARMLChannelMetadata, RMLMapping, RMLMappingHeader}
import streaming.core.channels._
import streaming.core.exceptions.{NoSuchChannelException, WrongSettingsException}
import streaming.core.utils.encryption.Implicits._
import streaming.core.utils.encryption.{CipherEncryptor, Encryptor}

import akka.Done
import com.mongodb.client.result.UpdateResult
import org.mongodb.scala.bson.BsonObjectId
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.{Filters, Projections}
import org.mongodb.scala.{Document, MongoClient, MongoClientSettings, MongoCollection, MongoDatabase}
import org.slf4j.{Logger, LoggerFactory}
import spray.json._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class MongoDBController[A](
                            databaseName: String,
                            clientSettings: MongoClientSettings,
                            channelManager: ChannelManager[A, CARMLChannelMetadata],
                            restoreChannelsStopped: Boolean,
                            authCipherSecret: String
                          )(
                            implicit executionContext: ExecutionContext
                          ) extends
  CARMLChannelController with RMLAnnotationsController with StatusController with RMLJsonSupport {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  val mongoClient: MongoClient = MongoClient(clientSettings)

  val mongoDatabase: MongoDatabase = mongoClient.getDatabase(databaseName)

  val annotationsCollection: MongoCollection[Document] = mongoDatabase.getCollection("annotations")
  val channelsCollection: MongoCollection[Document] = mongoDatabase.getCollection("channels")

  implicit val encryptor: Encryptor = CipherEncryptor(authCipherSecret)

  @volatile private var shuttingDown: Boolean = false

  @volatile private var initStatus: InitializationResult = InitializationResult(status = InitializationStatus.INITIALIZING)

  override def initializationResult: Future[InitializationResult] = Future.successful(initStatus)

  def setInitError(exception: Throwable): Unit =
    initStatus = InitializationResults.error(exception)

  def setInitSuccess(): Unit =
    initStatus = InitializationResults.initialized

  override def initialize: Future[Done] = {
    logger.info("Initializing")

    channelsCollection.createIndex(
      Document(JsObject(Map("settings.channelId" -> 1.toJson)).compactPrint)
    ).toFuture().onComplete({
      case Failure(exception) => setInitError(exception)
      case Success(_) => ()
    })

    //Restore channels from database
    logger.info("Loading channels from database")
    val initFuture = getAllChannels.flatMap(channels => {
      logger.info("Recreating channels")
      // Add all channels in a stopped state to the channel manager
      val stoppedChannels = channels.values.map(channel => {
        val restoredStatus = channel.streamControl.status
        val stoppedStatus = ChannelStatusInfo(
          isStopped = true,
          inputMonitorTopicEnabled = restoredStatus.inputMonitorTopicEnabled,
          outputMonitorTopicEnabled = restoredStatus.outputMonitorTopicEnabled,
          errorTopicEnabled = restoredStatus.errorTopicEnabled,
          error = restoredStatus.error
        )

        channelManager.createChannelFromMaterializedSettings(
          channelSettings = channel.settings,
          transformation = channel.transformation,
          channelMetadata = channel.metadata,
          status = stoppedStatus
        ).map(materializedChannel => {
          // Tentatively update channel state to "stopped"
          putChannelUpdatesInDatabase(
            materializedChannel.settings.channelId,
            ChannelUpdatesMade(channelRunning = Some(false)))
          (materializedChannel.settings.channelId, restoredStatus)
        })
      })

      if (!restoreChannelsStopped) {
        // Attempt to restart channels, that were running before last shutdown
        Future.sequence(stoppedChannels).map(channels => {
          logger.info("Restoring channels state")
          // TODO: Options for throttling channel restore speed, not to overwhelm brokers with initial connections
          channels.map { case (channelId, restoredStatus) =>
            if (!restoredStatus.isStopped)
              restartChannel(channelId)
          }
        }).map(_ => Done)
      } else {
        Future.successful(Done)
      }
    })

    initFuture.onComplete(tryResult => {
      logger.info("Recreated channels")
      tryResult match {
        case Failure(exception) =>
          setInitError(exception)
        case Success(_) =>
          setInitSuccess()
      }
    })

    initFuture

  }

  override def shutdown: Future[Done] = {
    logger.info("Closing MongoDB client connection")
    mongoClient.close()
    logger.info("Closed MongoDB client connection")
    logger.info("Shuting down channels")
    Future.sequence(shutdownChannels()).map(_ => {
      logger.info("Shut down channels")
      Done
    })
  }

  def documentToObject[O](document: Document)(implicit jsonReader: JsonReader[O]): O = document.toJson().parseJson.convertTo[O]

  def documentToIdObjectPair[O](document: Document)(implicit jsonReader: JsonReader[O]): (String, O) =
    document.getObjectId("_id").toString -> documentToObject[O](document)

  def documentToIdJsonPair[O](document: Document)(implicit jsonFormat: JsonFormat[O]): (String, JsObject) =
    document.getObjectId("_id").toString -> document.toJson().parseJson.convertTo[O].toJson.asJsObject

  def documentToIdJsonPairDirect(document: Document): (String, JsObject) =
    document.getObjectId("_id").toString -> JsObject(document.toJson().parseJson.asJsObject.fields - "_id")

  /* --- Channels --- */

  override def shutdownChannels(): List[Future[Done]] = {
    shuttingDown = true
    channelManager.stopAndRemoveAllChannels()
  }

  def channelByIdFilter(id: String): Bson = Filters.eq("settings.channelId", id)

  def noSuchChannel(channelId: String, extraText: String = "") = throw new NoSuchChannelException(s"No channel with ID: $channelId$extraText")

  def documentToChannelIdObjectPair(document: Document): (String, MaterializedChannel[CARMLChannelMetadata]) = {
    // TODO: How to guard against corrupted data, that does not have the nested settings.channelId key?
    val id = document.get("settings").get.asDocument().get("channelId").asString.getValue
    id -> MaterializedCARMLChannelDeserializer.deserializeToInactiveObject(document.toJson.parseJson.asJsObject)
  }

  def documentToChannelIdJsonPairDirect(document: Document): (String, JsObject) = {
    // TODO: How to guard against corrupted data, that does not have the nested settings.channelId key?
    val id = document.get("settings").get.asDocument().get("channelId").asString.getValue
    id -> JsObject(document.toJson().parseJson.asJsObject.fields - "_id")
  }

  def subscribeToChannelCompletion(materializedChannel: MaterializedChannel[CARMLChannelMetadata]): Unit = {
    // Subscribe to channel status changes to update them in the database automatically
    materializedChannel.streamControl.completion.onComplete(tryDone => {
      // Don't update database, if shutting down the streamer, to preserve channel completion state from before shutdown
      if (!shuttingDown) {
        tryDone match {
          case Success(done) =>
            updateChannelStatusInDatabase(
              materializedChannel.settings.channelId,
              ChannelStatusInfoOptions(isStopped = Some(true))
            )
          case Failure(exception) =>
            updateChannelStatusInDatabase(
              materializedChannel.settings.channelId,
              ChannelStatusInfoOptions(isStopped = Some(true), error = Some(s"$exception"))
            )
        }
      }
    })
  }

  override def getAllChannels: Future[Map[String, MaterializedChannel[CARMLChannelMetadata]]] =
    channelsCollection.find().toFuture()
      .map(_.map(documentToChannelIdObjectPair).map { case (str, channel) => (str, channel.decryptAuthData) }.toMap)

  override def getAllChannelsAsJson(includeAll: Boolean, includeStatus: Boolean, includeMetadata: Boolean, includeSettings: Boolean, includeRml: Boolean): Future[Map[String, JsObject]] = {
    val documents = if (includeAll || (includeStatus && includeMetadata && includeSettings && includeRml)) {
      channelsCollection.find()
    } else {
      val filterFields = List(
        if (includeStatus) "status" else "",
        if (includeMetadata) "metadata" else "",
        if (includeSettings) "settings" else "settings.channelId", // Always include channelId, so that the channelId -> channel JSON map can be constructed
      ).filter(x => x != "")

      val includeProjection = Projections.include(filterFields: _*)

      val foundWithIncluded = channelsCollection.find().projection(includeProjection)
      val projected = if (includeMetadata && !includeRml) foundWithIncluded.projection(Projections.exclude("metadata.mapping.rml")) else foundWithIncluded
      projected
    }

    documents.toFuture.map(_.map(document => {
      val (id, jsObject) = documentToChannelIdJsonPairDirect(document)
      if (!includeAll && !includeSettings) {
        // Remove settings key, which is always included, so that the channelId -> channel JSON map can be constructed
        (id, JsObject(jsObject.fields - "settings"))
      } else {
        (id, jsObject)
      }
    }).toMap)
  }

  override def getChannel(channelId: String): Future[MaterializedChannel[CARMLChannelMetadata]] = {
    channelsCollection.find(channelByIdFilter(channelId)).toFuture()
      .map({
        case head :: _ => MaterializedCARMLChannelDeserializer
          .deserializeToInactiveObject(head.toJson.parseJson.asJsObject)
          .decryptAuthData
        case Nil => noSuchChannel(channelId)
      })
  }

  override def getChannelAsJson(channelId: String)(includeAll: Boolean, includeStatus: Boolean, includeMetadata: Boolean, includeSettings: Boolean, includeRml: Boolean): Future[JsObject] = {
    // TODO: Refactor to decrease code duplication with getAllChannelsAsJson
    if (includeAll || (includeStatus && includeMetadata && includeSettings && includeRml)) {
      getChannel(channelId).map(channel => channel.serializeToJson(includeAll = true))
    } else {
      val filterFields = List(
        if (includeStatus) "status" else "",
        if (includeMetadata) "metadata" else "",
        if (includeSettings) "settings" else "settings.channelId", // Always include channelId, so that the channelId -> channel JSON map can be constructed
      ).filter(x => x != "")

      val includeProjection = Projections.include(filterFields: _*)
      val foundWithIncluded = channelsCollection.find(channelByIdFilter(channelId)).projection(includeProjection)
      val projected = if (includeMetadata && !includeRml) foundWithIncluded.projection(Projections.exclude("metadata.mapping.rml")) else foundWithIncluded
      projected.toFuture()
        .map({
          case head :: _ =>
            val fields = head.toJson().parseJson.asJsObject.fields - "_id"
            if (!includeAll && !includeSettings) {
              // Remove settings key, which is always included, so that the channelId -> channel JSON map can be constructed
              JsObject(fields - "settings")
            } else {
              JsObject(fields)
            }
          case Nil => noSuchChannel(channelId)
        })
    }
  }

  override def addChannel(channelInfo: ChannelInfo[CARMLChannelMetadata]): Future[MaterializedChannel[CARMLChannelMetadata]] = {
    channelInfo.metadata.mapping.tryTransformationWithException()

    channelManager.createChannel(
      channelSettings = channelInfo.settings,
      transformation = channelInfo.metadata.transformation,
      channelMetadata = channelInfo.metadata,
      status = channelInfo.status
    )
      .flatMap(materializedChannel => {
        channelsCollection.insertOne(
          Document(materializedChannel.encryptAuthData.serializeToJson(includeAll = true).compactPrint)
        ).toFuture()
          .map(_ => {
            subscribeToChannelCompletion(materializedChannel)
            materializedChannel
          })
          // If there is an exception after adding a channel, but during storing it in the database, stop the channel, remove it, and rethrow the exception
          .recoverWith({
            case exception =>
              channelManager.stopAndRemoveChannel(materializedChannel.settings.channelId).transformWith(done => {
                throw exception
              }
              )
          })
      })
  }

  def deleteChannelFromDatabase(channelId: String, exceptionExtraText: String = ""): Future[Done] =
    channelsCollection.deleteOne(channelByIdFilter(channelId)).toFuture()
      .map(result => if (result.getDeletedCount == 1) Done else noSuchChannel(channelId, exceptionExtraText))

  override def stopAndRemoveChannel(channelId: String): Future[Done] = {
    channelManager.stopAndRemoveChannel(channelId)
      .flatMap(_ =>
        deleteChannelFromDatabase(channelId, " : Channel stopped and removed, but no channel found in database.")
      )
      .recoverWith({
        // If there still is a channel in the channel manager, don't remove it from the database
        case exception =>
          val deleteFromDatabaseFuture = if (channelManager.getChannelOption(channelId).isEmpty) {
            deleteChannelFromDatabase(channelId, " : Channel stopped, but no channel found in database.")
          } else {
            Future.successful(Done)
          }
          deleteFromDatabaseFuture.transformWith(
            throw exception
          )
      })
  }

  override def restartChannel(channelId: String): Future[MaterializedChannel[CARMLChannelMetadata]] =
    channelManager.restartChannel(channelId).
      flatMap(materializedChannel => {
        // Assume, that channel is running, because if it stops after this point, the status will be updated correctly with the onComplete "subscription"
        // Clear error in channel status in database
        putChannelUpdatesInDatabase(channelId, ChannelUpdatesMade(channelRunning = Some(true)), clearError = true)
          .map(_ => {
            // Resubscribe to channel completed updates
            subscribeToChannelCompletion(materializedChannel)
            materializedChannel
          }
          )
      })

  def updateChannelStatusInDatabase(channelId: String, status: ChannelStatusInfoOptions, clearError: Boolean = false): Future[UpdateResult] = {
    val updatedFields = status.toJson.asJsObject.fields.map {
      case (key, value) =>
        (s"status.$key", value)
    }
    //    val setCommands = if (updatedFields.isEmpty) Map[String, JsObject]() else Map(
    //      "$set" -> JsObject(updatedFields)
    //    )
    // TODO: Test what happens, if the update object contains no updates
    val setCommands = Map(
      "$set" -> JsObject(updatedFields)
    )

    val updateCommands = if (clearError && status.error.isEmpty) {
      // Add unset command, if we want to clear the error, and there is no new error in the update (which would overwrite the error anyway)
      setCommands + ("$unset" -> JsObject("status.error" -> "".toJson))
    } else {
      setCommands
    }

    // TODO: Is there a better, more "scala" way of doing nested object updates?
    val statusUpdateObject = Document(
      JsObject(
        updateCommands
      ).compactPrint)
    channelsCollection.updateOne(
      channelByIdFilter(channelId),
      statusUpdateObject,
    ).toFuture()
  }

  def putChannelUpdatesInDatabase(channelId: String, updates: ChannelUpdatesMade, clearError: Boolean = false): Future[UpdateResult] =
    updateChannelStatusInDatabase(channelId, updates.toChannelStatusInfoOptions, clearError)


  override def updateChannelStatus(channelId: String)(updates: ChannelStatusInfoOptions): Future[ChannelUpdatesMade] = {
    // TODO: Test this!

    val channel = channelManager.getChannel(channelId)

    // Check for empty settings object
    if (List(updates.isStopped,
      updates.inputMonitorTopicEnabled,
      updates.outputMonitorTopicEnabled,
      updates.errorTopicEnabled
    ).forall(_.isEmpty)) {
      throw new WrongSettingsException("ChannelStatus object was empty, so no updates were made.")
    }

    // Change topic settings and map them to changes made (if any)
    val topicControls = channel.streamControl.topicControls
    val topicChanges = List(
      (updates.inputMonitorTopicEnabled, topicControls.inputMonitor),
      (updates.outputMonitorTopicEnabled, topicControls.outputMonitor),
      (updates.errorTopicEnabled, topicControls.error)
    )
    val topicUpdates = topicChanges.map {
      case (toggle, control) => toggle match {
        case Some(true) if control.isDisabled() =>
          if (control.enable()) Some(true) else None
        case Some(false) if control.isEnabled() =>
          if (control.disable()) None else Some(false)
        case _ => None
      }
    }

    val topicChangesOnly = ChannelUpdatesMade(
      inputMonitorTopicEnabled = topicUpdates(0),
      outputMonitorTopicEnabled = topicUpdates(1),
      errorTopicEnabled = topicUpdates(2)
    )

    val topicUpdatesFuture = if (topicChangesOnly.topicsUpdated) putChannelUpdatesInDatabase(channelId, topicChangesOnly) else Future.successful()

    // Change channel running status, if requested
    val updatesFuture = topicUpdatesFuture.flatMap(_ => {
      updates.isStopped match {
        case Some(true) if !channel.streamControl.isStopped =>
          channel.streamControl.shutdown().map(_ => Some(false))
        case Some(false) if channel.streamControl.isStopped =>

          val channelRunningUpdateFuture =
            restartChannel(channelId)
              .map(_ => Some(true))

          channelRunningUpdateFuture
        case _ => Future.successful(None)
      }
    })

    // Collect changes made
    val changesMade = updatesFuture.map(channelRunningChanges =>
      ChannelUpdatesMade(
        channelRunning = channelRunningChanges,
        inputMonitorTopicEnabled = topicChangesOnly.inputMonitorTopicEnabled,
        outputMonitorTopicEnabled = topicChangesOnly.outputMonitorTopicEnabled,
        errorTopicEnabled = topicChangesOnly.errorTopicEnabled
      ))

    changesMade
  }

  /* --- Annotations --- */

  def noSuchAnnotation(annotationId: String, extraText: String = "") = throw new NoSuchAnnotationException(s"No annotation with ID: $annotationId$extraText")

  def annotationByIdFilter(id: String): Bson = Try(BsonObjectId(id)) match {
    case Failure(exception) => noSuchAnnotation(id, s": $exception")
    case Success(bsonObjectId) => Filters.eq("_id", bsonObjectId)
  }

  override def getAllAnnotations: Future[Map[String, RMLMapping]] =
    annotationsCollection.find().toFuture()
      .map(_.map(documentToIdObjectPair[RMLMapping]).toMap)

  override def getAllAnnotationsAsJson(includeAll: Boolean, includeHeader: Boolean, includeRml: Boolean): Future[Map[String, JsObject]] = {
    val documents = if (includeAll || (includeHeader && includeRml)) {
      annotationsCollection.find()
    } else if (includeHeader) {
      val found = annotationsCollection.find()
      val projected = if (includeRml) found else found.projection(Projections.exclude("rml"))
      projected
    } else {
      annotationsCollection.find().projection(Projections.include("_id"))
    }

    documents.toFuture.map(_.map(documentToIdJsonPairDirect).toMap)
  }

  override def addAnnotation(rmlMapping: RMLMapping): Future[String] =
    annotationsCollection.insertOne(
      Document(rmlMapping.toJson.compactPrint)
    ).toFuture().map(res => res.getInsertedId.asObjectId().getValue.toString)


  override def getAnnotation(annotationId: String): Future[RMLMapping] =
    annotationsCollection.find(annotationByIdFilter(annotationId))
      .toFuture().map {
      case head :: rest => documentToObject[RMLMapping](head)
      case Nil => noSuchAnnotation(annotationId)
    }

  override def getAnnotationAsJson(annotationId: String)(includeAll: Boolean, includeHeader: Boolean, includeRml: Boolean): Future[JsObject] = {
    // TODO: Refactor this! - remove code duplication
    if (includeAll || (includeHeader && includeRml)) {
      getAnnotation(annotationId).map(_.toJson.asJsObject)
    } else if (includeHeader) {
      val found = annotationsCollection.find(annotationByIdFilter(annotationId))
      val projected = if (includeRml) found else found.projection(Projections.exclude("rml"))
      projected.toFuture()
        .map {
          case head :: _ => documentToObject[RMLMappingHeader](head)
          case Nil => noSuchAnnotation(annotationId)
        }
        .map(_.toJson.asJsObject)
    } else {
      // Only confirm, that the annotation exists, return empty object
      annotationsCollection.find(
        annotationByIdFilter(annotationId)
      ).projection(
        Projections.include("_id")
      ).toFuture().map {
        case c if c.nonEmpty => JsObject()
        case _ => noSuchAnnotation(annotationId)
      }
    }
  }

  override def removeAnnotation(annotationId: String): Future[Done] =
    annotationsCollection.deleteOne(annotationByIdFilter(annotationId)).toFuture()
      .map(result => if (result.getDeletedCount == 1) Done else noSuchAnnotation(annotationId))
}
