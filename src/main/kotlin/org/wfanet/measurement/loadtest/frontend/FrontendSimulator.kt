// Copyright 2021 The Cross-Media Measurement Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.wfanet.measurement.loadtest.frontend

import com.google.common.truth.Truth.assertThat
import com.google.protobuf.ByteString
import java.nio.file.Paths
import java.time.Duration
import java.util.logging.Logger
import kotlin.random.Random
import kotlinx.coroutines.delay
import org.wfanet.anysketch.AnySketch
import org.wfanet.anysketch.Sketch
import org.wfanet.anysketch.SketchProtos
import org.wfanet.estimation.Estimators
import org.wfanet.estimation.ValueHistogram
import org.wfanet.measurement.api.v2alpha.Certificate
import org.wfanet.measurement.api.v2alpha.CertificatesGrpcKt.CertificatesCoroutineStub
import org.wfanet.measurement.api.v2alpha.DataProvider
import org.wfanet.measurement.api.v2alpha.DataProviderCertificateKey
import org.wfanet.measurement.api.v2alpha.DataProviderKey
import org.wfanet.measurement.api.v2alpha.DataProvidersGrpcKt.DataProvidersCoroutineStub
import org.wfanet.measurement.api.v2alpha.DifferentialPrivacyParams
import org.wfanet.measurement.api.v2alpha.EncryptionPublicKey
import org.wfanet.measurement.api.v2alpha.EventGroup
import org.wfanet.measurement.api.v2alpha.EventGroupKey
import org.wfanet.measurement.api.v2alpha.EventGroupsGrpcKt.EventGroupsCoroutineStub
import org.wfanet.measurement.api.v2alpha.EventTemplates
import org.wfanet.measurement.api.v2alpha.GetDataProviderRequest
import org.wfanet.measurement.api.v2alpha.ListEventGroupsRequestKt
import org.wfanet.measurement.api.v2alpha.ListRequisitionsRequestKt
import org.wfanet.measurement.api.v2alpha.Measurement
import org.wfanet.measurement.api.v2alpha.Measurement.DataProviderEntry
import org.wfanet.measurement.api.v2alpha.Measurement.Result
import org.wfanet.measurement.api.v2alpha.MeasurementConsumer
import org.wfanet.measurement.api.v2alpha.MeasurementConsumersGrpcKt.MeasurementConsumersCoroutineStub
import org.wfanet.measurement.api.v2alpha.MeasurementKt
import org.wfanet.measurement.api.v2alpha.MeasurementKt.ResultKt.frequency
import org.wfanet.measurement.api.v2alpha.MeasurementKt.ResultKt.reach
import org.wfanet.measurement.api.v2alpha.MeasurementKt.dataProviderEntry
import org.wfanet.measurement.api.v2alpha.MeasurementKt.result
import org.wfanet.measurement.api.v2alpha.MeasurementSpec
import org.wfanet.measurement.api.v2alpha.MeasurementSpecKt.duration
import org.wfanet.measurement.api.v2alpha.MeasurementSpecKt.impression
import org.wfanet.measurement.api.v2alpha.MeasurementSpecKt.reachAndFrequency
import org.wfanet.measurement.api.v2alpha.MeasurementSpecKt.vidSamplingInterval
import org.wfanet.measurement.api.v2alpha.MeasurementsGrpcKt.MeasurementsCoroutineStub
import org.wfanet.measurement.api.v2alpha.ProtocolConfig
import org.wfanet.measurement.api.v2alpha.Requisition
import org.wfanet.measurement.api.v2alpha.RequisitionSpecKt
import org.wfanet.measurement.api.v2alpha.RequisitionSpecKt.eventFilter
import org.wfanet.measurement.api.v2alpha.RequisitionSpecKt.eventGroupEntry
import org.wfanet.measurement.api.v2alpha.RequisitionsGrpcKt.RequisitionsCoroutineStub
import org.wfanet.measurement.api.v2alpha.SignedData
import org.wfanet.measurement.api.v2alpha.createMeasurementRequest
import org.wfanet.measurement.api.v2alpha.getCertificateRequest
import org.wfanet.measurement.api.v2alpha.getMeasurementConsumerRequest
import org.wfanet.measurement.api.v2alpha.getMeasurementRequest
import org.wfanet.measurement.api.v2alpha.listEventGroupsRequest
import org.wfanet.measurement.api.v2alpha.listRequisitionsRequest
import org.wfanet.measurement.api.v2alpha.measurement
import org.wfanet.measurement.api.v2alpha.measurementSpec
import org.wfanet.measurement.api.v2alpha.requisitionSpec
import org.wfanet.measurement.common.crypto.PrivateKeyHandle
import org.wfanet.measurement.common.crypto.SigningKeyHandle
import org.wfanet.measurement.common.crypto.hashSha256
import org.wfanet.measurement.common.crypto.readCertificate
import org.wfanet.measurement.common.flatten
import org.wfanet.measurement.common.identity.apiIdToExternalId
import org.wfanet.measurement.common.loadLibrary
import org.wfanet.measurement.consent.client.measurementconsumer.decryptResult
import org.wfanet.measurement.consent.client.measurementconsumer.encryptRequisitionSpec
import org.wfanet.measurement.consent.client.measurementconsumer.signMeasurementSpec
import org.wfanet.measurement.consent.client.measurementconsumer.signRequisitionSpec
import org.wfanet.measurement.consent.client.measurementconsumer.verifyResult
import org.wfanet.measurement.kingdom.service.api.v2alpha.withAuthenticationKey
import org.wfanet.measurement.loadtest.storage.SketchStore

private const val DATA_PROVIDER_WILDCARD = "dataProviders/-"

data class MeasurementConsumerData(
  // The MC's public API resource name
  val name: String,
  /** The MC's consent signaling signing key. */
  val signingKey: SigningKeyHandle,
  /** The MC's encryption public key. */
  val encryptionKey: PrivateKeyHandle,
  /** An API key for the MC. */
  val apiAuthenticationKey: String
)

/** A simulator performing frontend operations. */
class FrontendSimulator(
  private val measurementConsumerData: MeasurementConsumerData,
  private val outputDpParams: DifferentialPrivacyParams,
  private val dataProvidersClient: DataProvidersCoroutineStub,
  private val eventGroupsClient: EventGroupsCoroutineStub,
  private val measurementsClient: MeasurementsCoroutineStub,
  private val requisitionsClient: RequisitionsCoroutineStub,
  private val measurementConsumersClient: MeasurementConsumersCoroutineStub,
  private val certificatesClient: CertificatesCoroutineStub,
  private val sketchStore: SketchStore,
  /** Map of event template names to filter expressions. */
  private val eventTemplateFilters: Map<String, String> = emptyMap()
) {
  /** Cache of resource name to [Certificate]. */
  private val certificateCache = mutableMapOf<String, Certificate>()

  /** A sequence of operations done in the simulator involving a reach and frequency measurement. */
  suspend fun executeReachAndFrequency(runId: String) {
    // Create a new measurement on behalf of the measurement consumer.
    val measurementConsumer = getMeasurementConsumer(measurementConsumerData.name)
    val createdReachAndFrequencyMeasurement =
      createMeasurement(measurementConsumer, runId, ::newReachAndFrequencyMeasurementSpec)
    logger.info(
      "Created reach and frequency measurement ${createdReachAndFrequencyMeasurement.name}."
    )

    // Get the CMMS computed result and compare it with the expected result.
    var mpcResult = getComputedResult(createdReachAndFrequencyMeasurement.name)
    while (mpcResult == null) {
      logger.info("Computation not done yet, wait for another 30 seconds.")
      delay(Duration.ofSeconds(30).toMillis())
      mpcResult = getComputedResult(createdReachAndFrequencyMeasurement.name)
    }
    logger.info("Got computed result from Kingdom: $mpcResult")

    val liquidLegionV2Protocol = createdReachAndFrequencyMeasurement.protocolConfig.liquidLegionsV2
    val expectedResult =
      getExpectedResult(createdReachAndFrequencyMeasurement.name, liquidLegionV2Protocol)
    logger.info("Expected result: $expectedResult")

    assertDpResultsEqual(
      expectedResult,
      mpcResult,
      liquidLegionV2Protocol.maximumFrequency.toLong()
    )
    logger.info("Computed result is equal to the expected result. Correctness Test passes.")
  }

  /** A sequence of operations done in the simulator involving an impression measurement. */
  suspend fun executeImpression(runId: String) {
    // Create a new measurement on behalf of the measurement consumer.
    val measurementConsumer = getMeasurementConsumer(measurementConsumerData.name)
    val createdImpressionMeasurement =
      createMeasurement(measurementConsumer, runId, ::newImpressionMeasurementSpec)
    logger.info("Created impression measurement ${createdImpressionMeasurement.name}.")

    var impressionResults = getImpressionResults(createdImpressionMeasurement.name)
    while (impressionResults.isEmpty()) {
      logger.info("Fulfillment not done yet, wait for another 30 seconds.")
      delay(Duration.ofSeconds(30).toMillis())
      impressionResults = getImpressionResults(createdImpressionMeasurement.name)
    }

    impressionResults.forEach {
      val result = parseAndVerifyResult(it)
      assertThat(result.impression.value)
        .isEqualTo(
          // EdpSimulator sets it to this value.
          apiIdToExternalId(DataProviderCertificateKey.fromName(it.certificate)!!.dataProviderId)
        )
    }
    logger.info("Impression result is equal to the expected result. Correctness Test passes.")
  }

  /** A sequence of operations done in the simulator involving a duration measurement. */
  suspend fun executeDuration(runId: String) {
    // Create a new measurement on behalf of the measurement consumer.
    val measurementConsumer = getMeasurementConsumer(measurementConsumerData.name)
    val createdDurationMeasurement =
      createMeasurement(measurementConsumer, runId, ::newDurationMeasurementSpec)
    logger.info("Created duration measurement ${createdDurationMeasurement.name}.")

    var durationResults = getDurationResults(createdDurationMeasurement.name)
    while (durationResults.isEmpty()) {
      logger.info("Fulfillment not done yet, wait for another 30 seconds.")
      delay(Duration.ofSeconds(30).toMillis())
      durationResults = getDurationResults(createdDurationMeasurement.name)
    }

    durationResults.forEach {
      val result = parseAndVerifyResult(it)
      assertThat(result.watchDuration.value.seconds)
        .isEqualTo(
          // EdpSimulator sets it to this value.
          apiIdToExternalId(DataProviderCertificateKey.fromName(it.certificate)!!.dataProviderId)
        )
    }
    logger.info("Duration result is equal to the expected result. Correctness Test passes.")
  }

  /** Compare two [Result]s within the differential privacy error range. */
  private fun assertDpResultsEqual(
    expectedResult: Result,
    actualResult: Result,
    maximumFrequency: Long
  ) {
    val reachRatio = expectedResult.reach.value.toDouble() / actualResult.reach.value.toDouble()
    assertThat(reachRatio).isWithin(0.02).of(1.0)
    (1L..maximumFrequency).forEach {
      val expected = expectedResult.frequency.relativeFrequencyDistributionMap.getOrDefault(it, 0.0)
      val actual = actualResult.frequency.relativeFrequencyDistributionMap.getOrDefault(it, 0.0)
      assertThat(actual).isWithin(0.05).of(expected)
    }
  }

  /** Creates a Measurement on behalf of the [MeasurementConsumer]. */
  private suspend fun createMeasurement(
    measurementConsumer: MeasurementConsumer,
    runId: String,
    newMeasurementSpec:
      (
        serializedMeasurementPublicKey: ByteString,
        nonceHashes: MutableList<ByteString>) -> MeasurementSpec
  ): Measurement {
    val eventGroups = listEventGroups(measurementConsumer.name)

    val nonceHashes = mutableListOf<ByteString>()
    val dataProviderEntries =
      eventGroups.map {
        val nonce = Random.Default.nextLong()
        nonceHashes.add(hashSha256(nonce))
        createDataProviderEntry(it, measurementConsumer, nonce)
      }

    val request = createMeasurementRequest {
      measurement = measurement {
        measurementConsumerCertificate = measurementConsumer.certificate
        measurementSpec =
          signMeasurementSpec(
            newMeasurementSpec(measurementConsumer.publicKey.data, nonceHashes),
            measurementConsumerData.signingKey
          )
        dataProviders += dataProviderEntries
        this.measurementReferenceId = runId
      }
    }
    return measurementsClient
      .withAuthenticationKey(measurementConsumerData.apiAuthenticationKey)
      .createMeasurement(request)
  }

  /** Gets the result of a [Measurement] if it is succeeded. */
  private suspend fun getImpressionResults(measurementName: String): List<Measurement.ResultPair> {
    val measurement =
      measurementsClient
        .withAuthenticationKey(measurementConsumerData.apiAuthenticationKey)
        .getMeasurement(getMeasurementRequest { name = measurementName })
    logger.info("Current Measurement state is: " + measurement.state)
    if (measurement.state == Measurement.State.FAILED) {
      logger.warning("Failure reason: " + measurement.failure.reason)
      logger.warning("Failure message: " + measurement.failure.message)
    }
    return measurement.resultsList.toList()
  }

  /** Gets the result of a [Measurement] if it is succeeded. */
  private suspend fun getDurationResults(measurementName: String): List<Measurement.ResultPair> {
    val measurement =
      measurementsClient
        .withAuthenticationKey(measurementConsumerData.apiAuthenticationKey)
        .getMeasurement(getMeasurementRequest { name = measurementName })
    logger.info("Current Measurement state is: " + measurement.state)
    if (measurement.state == Measurement.State.FAILED) {
      logger.warning("Failure reason: " + measurement.failure.reason)
      logger.warning("Failure message: " + measurement.failure.message)
    }
    return measurement.resultsList.toList()
  }

  /** Gets the result of a [Measurement] if it is succeeded. */
  private suspend fun getComputedResult(measurementName: String): Result? {
    val measurement =
      measurementsClient
        .withAuthenticationKey(measurementConsumerData.apiAuthenticationKey)
        .getMeasurement(getMeasurementRequest { name = measurementName })
    logger.info("Current Measurement state is: " + measurement.state)
    if (measurement.state == Measurement.State.FAILED) {
      logger.warning("Failure reason: " + measurement.failure.reason)
      logger.warning("Failure message: " + measurement.failure.message)
    }
    if (measurement.state != Measurement.State.SUCCEEDED) {
      return null
    }

    val resultPair = measurement.resultsList[0]
    return parseAndVerifyResult(resultPair)
  }

  private suspend fun parseAndVerifyResult(resultPair: Measurement.ResultPair): Result {
    val certificate =
      certificateCache.getOrPut(resultPair.certificate) {
        certificatesClient
          .withAuthenticationKey(measurementConsumerData.apiAuthenticationKey)
          .getCertificate(getCertificateRequest { name = resultPair.certificate })
      }

    val signedResult =
      decryptResult(resultPair.encryptedResult, measurementConsumerData.encryptionKey)
    @Suppress("BlockingMethodInNonBlockingContext") // Not blocking I/O.
    val result = Result.parseFrom(signedResult.data)

    if (!verifyResult(signedResult.signature, result, readCertificate(certificate.x509Der))) {
      error("Signature of the result is invalid.")
    }
    return result
  }

  /** Gets the expected result of a [Measurement] using raw sketches. */
  suspend fun getExpectedResult(
    measurementName: String,
    protocolConfig: ProtocolConfig.LiquidLegionsV2
  ): Result {
    val requisitions = listRequisitions(measurementName)
    require(requisitions.isNotEmpty()) { "Requisition list is empty." }

    val anySketches =
      requisitions.map {
        val storedSketch =
          sketchStore.get(it)?.read()?.flatten() ?: error("Sketch blob not found for ${it.name}.")
        SketchProtos.toAnySketch(Sketch.parseFrom(storedSketch))
      }

    val combinedAnySketch = anySketches[0]
    if (anySketches.size > 1) {
      combinedAnySketch.apply { mergeAll(anySketches.subList(1, anySketches.size)) }
    }

    val expectedReach =
      estimateCardinality(
        combinedAnySketch,
        protocolConfig.sketchParams.decayRate,
        protocolConfig.sketchParams.maxSize
      )
    val expectedFrequency =
      estimateFrequency(combinedAnySketch, protocolConfig.maximumFrequency.toLong())
    return result {
      reach = reach { value = expectedReach }
      frequency = frequency { relativeFrequencyDistribution.putAll(expectedFrequency) }
    }
  }

  /** Estimates the cardinality of an [AnySketch]. */
  private fun estimateCardinality(anySketch: AnySketch, decayRate: Double, indexSize: Long): Long {
    val activeRegisterCount = anySketch.toList().size.toLong()
    return Estimators.EstimateCardinalityLiquidLegions(decayRate, indexSize, activeRegisterCount)
  }

  /** Estimates the relative frequency histogram of an [AnySketch]. */
  private fun estimateFrequency(anySketch: AnySketch, maximumFrequency: Long): Map<Long, Double> {
    val valueIndex = anySketch.getValueIndex("SamplingIndicator").asInt
    val actualHistogram =
      ValueHistogram.calculateHistogram(anySketch, "Frequency") { it.values[valueIndex] != -1L }
    val result = mutableMapOf<Long, Double>()
    actualHistogram.forEach {
      val key = minOf(it.key, maximumFrequency)
      result[key] = result.getOrDefault(key, 0.0) + it.value
    }
    return result
  }

  private suspend fun getMeasurementConsumer(name: String): MeasurementConsumer {
    val request = getMeasurementConsumerRequest { this.name = name }
    return measurementConsumersClient
      .withAuthenticationKey(measurementConsumerData.apiAuthenticationKey)
      .getMeasurementConsumer(request)
  }

  private fun newReachAndFrequencyMeasurementSpec(
    serializedMeasurementPublicKey: ByteString,
    nonceHashes: List<ByteString>
  ): MeasurementSpec {
    return measurementSpec {
      measurementPublicKey = serializedMeasurementPublicKey
      reachAndFrequency = reachAndFrequency {
        reachPrivacyParams = outputDpParams
        frequencyPrivacyParams = outputDpParams
        vidSamplingInterval = vidSamplingInterval { width = 1.0f }
      }
      this.nonceHashes += nonceHashes
    }
  }

  private fun newImpressionMeasurementSpec(
    serializedMeasurementPublicKey: ByteString,
    nonceHashes: List<ByteString>
  ): MeasurementSpec {
    return measurementSpec {
      measurementPublicKey = serializedMeasurementPublicKey
      impression = impression {
        privacyParams = outputDpParams
        maximumFrequencyPerUser = 1
      }
      this.nonceHashes += nonceHashes
    }
  }

  private fun newDurationMeasurementSpec(
    serializedMeasurementPublicKey: ByteString,
    nonceHashes: List<ByteString>
  ): MeasurementSpec {
    return measurementSpec {
      measurementPublicKey = serializedMeasurementPublicKey
      duration = duration {
        privacyParams = outputDpParams
        maximumWatchDurationPerUser = 1
      }
      this.nonceHashes += nonceHashes
    }
  }

  private suspend fun listEventGroups(measurementConsumer: String): List<EventGroup> {
    val request = listEventGroupsRequest {
      parent = DATA_PROVIDER_WILDCARD
      filter = ListEventGroupsRequestKt.filter { measurementConsumers += measurementConsumer }
    }
    return eventGroupsClient
      .withAuthenticationKey(measurementConsumerData.apiAuthenticationKey)
      .listEventGroups(request)
      .eventGroupsList
  }

  private suspend fun listRequisitions(measurement: String): List<Requisition> {
    val request = listRequisitionsRequest {
      parent = DATA_PROVIDER_WILDCARD
      filter = ListRequisitionsRequestKt.filter { this.measurement = measurement }
    }
    return requisitionsClient
      .withAuthenticationKey(measurementConsumerData.apiAuthenticationKey)
      .listRequisitions(request)
      .requisitionsList
  }

  private fun extractDataProviderName(eventGroupName: String): String {
    val eventGroupKey = EventGroupKey.fromName(eventGroupName) ?: error("Invalid eventGroup name.")
    return DataProviderKey(eventGroupKey.dataProviderId).toName()
  }

  private suspend fun getDataProvider(name: String): DataProvider {
    val request = GetDataProviderRequest.newBuilder().also { it.name = name }.build()
    return dataProvidersClient
      .withAuthenticationKey(measurementConsumerData.apiAuthenticationKey)
      .getDataProvider(request)
  }

  /**
   * Creates a CEL filter using Event Templates names to qualify each variable in expression.
   *
   * @param registeredEventTemplates Fully-qualified protobuf message types (e.g.
   * wfa.measurement.api.v2alpha.event_templates.testing.TestVideoTemplate)
   */
  private fun createFilterExpression(registeredEventTemplates: Iterable<String>): String {
    val eventGroupTemplateNameMap: Map<String, String> =
      registeredEventTemplates.associateWith { (EventTemplates.getEventTemplateForType(it)!!).name }

    return eventTemplateFilters
      .map {
        if (!eventGroupTemplateNameMap.containsKey(it.key)) {
          error("EventGroup is not registered to the template ${it.key}")
        }
        "${eventGroupTemplateNameMap[it.key]}.${it.value}"
      }
      .reduce { acc, string -> "$acc && $string" }
  }

  private suspend fun createDataProviderEntry(
    eventGroup: EventGroup,
    measurementConsumer: MeasurementConsumer,
    nonce: Long
  ): DataProviderEntry {
    val dataProvider = getDataProvider(extractDataProviderName(eventGroup.name))

    val eventFilterExpression =
      createFilterExpression(eventGroup.eventTemplatesList.map { it.type })

    val requisitionSpec = requisitionSpec {
      eventGroups += eventGroupEntry {
        key = eventGroup.name
        value =
          RequisitionSpecKt.EventGroupEntryKt.value {
            filter = eventFilter { expression = eventFilterExpression }
          }
      }
      measurementPublicKey = measurementConsumer.publicKey.data
      this.nonce = nonce
    }
    val signedRequisitionSpec =
      signRequisitionSpec(requisitionSpec, measurementConsumerData.signingKey)
    return dataProvider.toDataProviderEntry(signedRequisitionSpec, hashSha256(nonce))
  }

  private fun DataProvider.toDataProviderEntry(
    signedRequisitionSpec: SignedData,
    nonceHash: ByteString
  ): DataProviderEntry {
    val source = this
    return dataProviderEntry {
      key = source.name
      this.value =
        MeasurementKt.DataProviderEntryKt.value {
          dataProviderCertificate = source.certificate
          dataProviderPublicKey = source.publicKey
          encryptedRequisitionSpec =
            encryptRequisitionSpec(
              signedRequisitionSpec,
              EncryptionPublicKey.parseFrom(source.publicKey.data),
            )
          this.nonceHash = nonceHash
        }
    }
  }

  companion object {
    private val logger: Logger = Logger.getLogger(this::class.java.name)
    init {
      loadLibrary(
        name = "estimators",
        directoryPath =
          Paths.get("any_sketch_java", "src", "main", "java", "org", "wfanet", "estimation")
      )
    }
  }
}
