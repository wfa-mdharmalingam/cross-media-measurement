package org.wfanet.measurement.integration

import io.grpc.Channel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.logging.Logger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.wfanet.measurement.common.ExternalId
import org.wfanet.measurement.common.MinimumIntervalThrottler
import org.wfanet.measurement.common.identity.withDuchyIdentities
import org.wfanet.measurement.common.testing.CloseableResource
import org.wfanet.measurement.common.testing.chainRulesSequentially
import org.wfanet.measurement.common.testing.launchAsAutoCloseable
import org.wfanet.measurement.common.testing.withVerboseLogging
import org.wfanet.measurement.common.toProtoTime
import org.wfanet.measurement.db.kingdom.KingdomRelationalDatabase
import org.wfanet.measurement.internal.MetricDefinition
import org.wfanet.measurement.internal.SketchMetricDefinition
import org.wfanet.measurement.internal.kingdom.ReportConfig
import org.wfanet.measurement.internal.kingdom.ReportConfigSchedule
import org.wfanet.measurement.internal.kingdom.ReportConfigScheduleStorageGrpcKt.ReportConfigScheduleStorageCoroutineStub
import org.wfanet.measurement.internal.kingdom.ReportConfigStorageGrpcKt.ReportConfigStorageCoroutineStub
import org.wfanet.measurement.internal.kingdom.ReportLogEntryStorageGrpcKt.ReportLogEntryStorageCoroutineStub
import org.wfanet.measurement.internal.kingdom.ReportStorageGrpcKt.ReportStorageCoroutineStub
import org.wfanet.measurement.internal.kingdom.RequisitionStorageGrpcKt.RequisitionStorageCoroutineStub
import org.wfanet.measurement.internal.kingdom.TimePeriod
import org.wfanet.measurement.kingdom.Daemon
import org.wfanet.measurement.kingdom.DaemonDatabaseServicesClientImpl
import org.wfanet.measurement.kingdom.runReportMaker
import org.wfanet.measurement.kingdom.runReportStarter
import org.wfanet.measurement.kingdom.runRequisitionLinker
import org.wfanet.measurement.service.internal.kingdom.buildStorageServices
import org.wfanet.measurement.service.testing.GrpcTestServerRule
import org.wfanet.measurement.service.v1alpha.globalcomputation.GlobalComputationService
import org.wfanet.measurement.service.v1alpha.requisition.RequisitionService

/**
 * TestRule that starts and stops all Kingdom gRPC services and daemons.
 *
 * @param kingdomRelationalDatabaseProvider called exactly once to produce KingdomRelationalDatabase
 */
class InProcessKingdom(
  kingdomRelationalDatabaseProvider: () -> KingdomRelationalDatabase
) : TestRule {
  private val kingdomRelationalDatabase by lazy { kingdomRelationalDatabaseProvider() }

  private val databaseServices = GrpcTestServerRule {
    logger.info("Building Kingdom's internal services")
    for (service in buildStorageServices(kingdomRelationalDatabase)) {
      addService(service.withVerboseLogging())
    }
  }

  private val kingdomApiServices = GrpcTestServerRule {
    logger.info("Building Kingdom's public API services")
    val reportStorage = ReportStorageCoroutineStub(databaseServices.channel)
    val reportLogEntryStorage = ReportLogEntryStorageCoroutineStub(databaseServices.channel)
    val requisitionStorage = RequisitionStorageCoroutineStub(databaseServices.channel)

    addService(
      GlobalComputationService(reportStorage, reportLogEntryStorage)
        .withDuchyIdentities()
        .withVerboseLogging()
    )
    addService(
      RequisitionService(requisitionStorage)
        .withDuchyIdentities()
        .withVerboseLogging()
    )
  }

  private val daemonRunner = CloseableResource {
    GlobalScope.launchAsAutoCloseable {
      logger.info("Launching Kingdom's daemons")
      val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        logger.warning("Daemon exception: $exception")
      }
      supervisorScope {
        val reportConfigStorage = ReportConfigStorageCoroutineStub(databaseServices.channel)
        val reportConfigScheduleStorage =
          ReportConfigScheduleStorageCoroutineStub(databaseServices.channel)
        val reportStorage = ReportStorageCoroutineStub(databaseServices.channel)
        val requisitionStorage = RequisitionStorageCoroutineStub(databaseServices.channel)
        val daemonThrottler = MinimumIntervalThrottler(Clock.systemUTC(), Duration.ofMillis(1))
        val daemonDatabaseServicesClient = DaemonDatabaseServicesClientImpl(
          reportConfigStorage, reportConfigScheduleStorage, reportStorage, requisitionStorage
        )
        val daemon = Daemon(daemonThrottler, 1, daemonDatabaseServicesClient)
        launch(exceptionHandler) { daemon.runRequisitionLinker() }
        launch(exceptionHandler) { daemon.runReportStarter() }
        launch(exceptionHandler) { daemon.runReportMaker() }
      }
    }
  }

  /**
   * Provides a gRPC channel to the Kingdom's public APIs.
   */
  val publicApiChannel: Channel
    get() = kingdomApiServices.channel

  override fun apply(statement: Statement, description: Description): Statement {
    return chainRulesSequentially(databaseServices, kingdomApiServices, daemonRunner)
      .apply(statement, description)
  }

  data class SetupIdentifiers(
    val externalDataProviderId1: ExternalId,
    val externalDataProviderId2: ExternalId,
    val externalCampaignId1: ExternalId,
    val externalCampaignId2: ExternalId
  )

  /**
   * Adds an Advertiser, two DataProviders, two Campaigns, a ReportConfig, and a
   * ReportConfigSchedule to [kingdomRelationalDatabase].
   */
  fun populateKingdomRelationalDatabase(): SetupIdentifiers {
    val advertiser = kingdomRelationalDatabase.createAdvertiser()
    logger.info("Created an Advertiser: $advertiser")

    val dataProvider1 = kingdomRelationalDatabase.createDataProvider()
    logger.info("Created a DataProvider: $dataProvider1")

    val dataProvider2 = kingdomRelationalDatabase.createDataProvider()
    logger.info("Created a DataProvider: $dataProvider2")

    val externalAdvertiserId = ExternalId(advertiser.externalAdvertiserId)
    val externalDataProviderId1 = ExternalId(dataProvider1.externalDataProviderId)
    val externalDataProviderId2 = ExternalId(dataProvider2.externalDataProviderId)

    val campaign1 = kingdomRelationalDatabase.createCampaign(
      externalDataProviderId1,
      externalAdvertiserId,
      "Springtime Sale Campaign"
    )
    logger.info("Created a Campaign: $campaign1")

    val campaign2 = kingdomRelationalDatabase.createCampaign(
      externalDataProviderId2,
      externalAdvertiserId,
      "Summer Savings Campaign"
    )
    logger.info("Created a Campaign: $campaign2")

    val externalCampaignId1 = ExternalId(campaign1.externalCampaignId)
    val externalCampaignId2 = ExternalId(campaign2.externalCampaignId)

    val metricDefinition = MetricDefinition.newBuilder().apply {
      sketchBuilder.apply {
        sketchConfigId = 12345L
        type = SketchMetricDefinition.Type.IMPRESSION_REACH_AND_FREQUENCY
      }
    }.build()

    val reportConfig = kingdomRelationalDatabase.createReportConfig(
      ReportConfig.newBuilder()
        .setExternalAdvertiserId(externalAdvertiserId.value)
        .apply {
          numRequisitions = 2
          reportConfigDetailsBuilder.apply {
            addMetricDefinitions(metricDefinition)
            reportDurationBuilder.apply {
              unit = TimePeriod.Unit.DAY
              count = 7
            }
          }
        }
        .build(),
      listOf(externalCampaignId1, externalCampaignId2)
    )
    logger.info("Created a ReportConfig: $reportConfig")

    val externalReportConfigId = ExternalId(reportConfig.externalReportConfigId)

    val schedule = kingdomRelationalDatabase.createSchedule(
      ReportConfigSchedule.newBuilder()
        .setExternalAdvertiserId(externalAdvertiserId.value)
        .setExternalReportConfigId(externalReportConfigId.value)
        .apply {
          repetitionSpecBuilder.apply {
            start = Instant.now().toProtoTime()
            repetitionPeriodBuilder.apply {
              unit = TimePeriod.Unit.DAY
              count = 7
            }
          }
          nextReportStartTime = repetitionSpec.start
        }
        .build()
    )
    logger.info("Created a ReportConfigSchedule: $schedule")

    return SetupIdentifiers(
      externalDataProviderId1,
      externalDataProviderId2,
      externalCampaignId1,
      externalCampaignId2
    )
  }

  companion object {
    private val logger: Logger = Logger.getLogger(this::class.java.name)
  }
}
