package org.wfanet.measurement.db.kingdom.gcp

import com.google.common.truth.extensions.proto.ProtoTruth.assertThat
import java.time.Instant
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.wfanet.measurement.common.ExternalId
import org.wfanet.measurement.common.toInstant
import org.wfanet.measurement.db.kingdom.StreamReportsFilter
import org.wfanet.measurement.db.kingdom.gcp.testing.KingdomDatabaseTestBase
import org.wfanet.measurement.db.kingdom.streamReportsFilter
import org.wfanet.measurement.internal.kingdom.Report
import org.wfanet.measurement.internal.kingdom.Report.ReportState

@RunWith(JUnit4::class)
class StreamReportsQueryTest : KingdomDatabaseTestBase() {
  companion object {
    const val UNUSED_ID = 999999L

    const val ADVERTISER_ID = 1L
    const val REPORT_CONFIG_ID = 2L
    const val SCHEDULE_ID = 3L
    const val EXTERNAL_ADVERTISER_ID = 4L
    const val EXTERNAL_REPORT_CONFIG_ID = 5L
    const val EXTERNAL_SCHEDULE_ID = 6L

    const val REPORT_ID1 = 7L
    const val REPORT_ID2 = 8L
    const val REPORT_ID3 = 9L

    const val EXTERNAL_REPORT_ID1 = 10L
    const val EXTERNAL_REPORT_ID2 = 11L
    const val EXTERNAL_REPORT_ID3 = 12L

    val REPORT1: Report = Report.newBuilder().apply {
      externalAdvertiserId = EXTERNAL_ADVERTISER_ID
      externalReportConfigId = EXTERNAL_REPORT_CONFIG_ID
      externalScheduleId = EXTERNAL_SCHEDULE_ID
      externalReportId = EXTERNAL_REPORT_ID1
    }.build()

    val REPORT2: Report = REPORT1.toBuilder().setExternalReportId(EXTERNAL_REPORT_ID2).build()
    val REPORT3: Report = REPORT1.toBuilder().setExternalReportId(EXTERNAL_REPORT_ID3).build()
  }

  private fun executeToList(filter: StreamReportsFilter, limit: Long): List<Report> =
    runBlocking {
      StreamReportsQuery().execute(
        databaseClient.singleUse(),
        filter,
        limit
      ).toList()
    }

  @Before
  fun populateDatabase() {
    insertAdvertiser(ADVERTISER_ID, EXTERNAL_ADVERTISER_ID)
    insertReportConfig(ADVERTISER_ID, REPORT_CONFIG_ID, EXTERNAL_REPORT_CONFIG_ID)
    insertReportConfigSchedule(ADVERTISER_ID, REPORT_CONFIG_ID, SCHEDULE_ID, EXTERNAL_SCHEDULE_ID)

    fun insertReportWithIds(reportId: Long, externalReportId: Long) =
      insertReport(
        ADVERTISER_ID, REPORT_CONFIG_ID, SCHEDULE_ID, reportId, externalReportId,
        state = ReportState.READY_TO_START
      )

    insertReportWithIds(REPORT_ID1, EXTERNAL_REPORT_ID1)
    insertReportWithIds(REPORT_ID2, EXTERNAL_REPORT_ID2)
    insertReportWithIds(REPORT_ID3, EXTERNAL_REPORT_ID3)
  }

  @Test
  fun limits() = runBlocking<Unit> {
    assertThat(executeToList(streamReportsFilter(), 10))
      .comparingExpectedFieldsOnly()
      .containsExactly(REPORT1, REPORT2, REPORT3)
      .inOrder()

    assertThat(executeToList(streamReportsFilter(), 2))
      .comparingExpectedFieldsOnly()
      .containsExactly(REPORT1, REPORT2)
      .inOrder()

    assertThat(executeToList(streamReportsFilter(), 1))
      .comparingExpectedFieldsOnly()
      .containsExactly(REPORT1)

    assertThat(executeToList(streamReportsFilter(), 0))
      .isEmpty()
  }

  @Test
  fun `create time`() = runBlocking<Unit> {
    fun executeWithTimeFilter(time: Instant) =
      executeToList(streamReportsFilter(createdAfter = time), 100)

    val all = executeWithTimeFilter(Instant.EPOCH)

    assertThat(all)
      .comparingExpectedFieldsOnly()
      .containsExactly(REPORT1, REPORT2, REPORT3)
      .inOrder()

    assertThat(executeWithTimeFilter(all[0].createTime.toInstant()))
      .comparingExpectedFieldsOnly()
      .containsExactly(REPORT2, REPORT3)
  }

  @Test
  fun `external id filters`() = runBlocking<Unit> {
    fun wrongIdIf(condition: Boolean, id: Long) = ExternalId(if (condition) UNUSED_ID else id)

    repeat(3) {
      val filter = streamReportsFilter(
        externalAdvertiserIds = listOf(wrongIdIf(it == 0, EXTERNAL_ADVERTISER_ID)),
        externalReportConfigIds = listOf(wrongIdIf(it == 1, EXTERNAL_REPORT_CONFIG_ID)),
        externalScheduleIds = listOf(wrongIdIf(it == 2, EXTERNAL_SCHEDULE_ID))
      )
      assertThat(executeToList(filter, 10))
        .isEmpty()
    }
  }

  @Test
  fun `all filters`() {
    val filter = streamReportsFilter(
      externalAdvertiserIds = listOf(ExternalId(EXTERNAL_ADVERTISER_ID)),
      externalReportConfigIds = listOf(ExternalId(EXTERNAL_REPORT_CONFIG_ID)),
      externalScheduleIds = listOf(ExternalId(EXTERNAL_SCHEDULE_ID)),
      states = listOf(ReportState.READY_TO_START),
      createdAfter = Instant.EPOCH
    )
    assertThat(executeToList(filter, 10))
      .comparingExpectedFieldsOnly()
      .containsExactly(REPORT1, REPORT2, REPORT3)
      .inOrder()
  }
}
