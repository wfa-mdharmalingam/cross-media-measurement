// Copyright 2020 The Measurement System Authors
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

package org.wfanet.measurement.service.internal.kingdom

import com.google.common.truth.extensions.proto.ProtoTruth.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.wfanet.measurement.common.ExternalId
import org.wfanet.measurement.db.kingdom.KingdomRelationalDatabase
import org.wfanet.measurement.internal.kingdom.ListRequisitionTemplatesRequest
import org.wfanet.measurement.internal.kingdom.ListRequisitionTemplatesResponse
import org.wfanet.measurement.internal.kingdom.ReportConfigStorageGrpcKt.ReportConfigStorageCoroutineStub
import org.wfanet.measurement.internal.kingdom.RequisitionTemplate
import org.wfanet.measurement.service.testing.GrpcTestServerRule

private const val EXTERNAL_REPORT_CONFIG_ID = 1L

private val REQUISITION_TEMPLATE1: RequisitionTemplate = RequisitionTemplate.newBuilder().apply {
  externalDataProviderId = 2
  externalCampaignId = 3
  requisitionDetailsBuilder.metricDefinitionBuilder.sketchBuilder.sketchConfigId = 4
}.build()

private val REQUISITION_TEMPLATE2: RequisitionTemplate = RequisitionTemplate.newBuilder().apply {
  externalDataProviderId = 5
  externalCampaignId = 6
  requisitionDetailsBuilder.metricDefinitionBuilder.sketchBuilder.sketchConfigId = 7
}.build()

@RunWith(JUnit4::class)
class ReportConfigStorageServiceTest {

  private val kingdomRelationalDatabase: KingdomRelationalDatabase = mock() {
    on { listRequisitionTemplates(any()) }
      .thenReturn(listOf(REQUISITION_TEMPLATE1, REQUISITION_TEMPLATE2))
  }

  @get:Rule
  val grpcTestServerRule = GrpcTestServerRule {
    listOf(ReportConfigStorageService(kingdomRelationalDatabase))
  }

  private val stub by lazy { ReportConfigStorageCoroutineStub(grpcTestServerRule.channel) }

  @Test
  fun listRequisitionTemplates() = runBlocking<Unit> {
    val request =
      ListRequisitionTemplatesRequest.newBuilder()
        .setExternalReportConfigId(EXTERNAL_REPORT_CONFIG_ID)
        .build()

    val expectedResponse =
      ListRequisitionTemplatesResponse.newBuilder()
        .addRequisitionTemplates(REQUISITION_TEMPLATE1)
        .addRequisitionTemplates(REQUISITION_TEMPLATE2)
        .build()

    assertThat(stub.listRequisitionTemplates(request))
      .ignoringRepeatedFieldOrder()
      .isEqualTo(expectedResponse)

    verify(kingdomRelationalDatabase)
      .listRequisitionTemplates(ExternalId(EXTERNAL_REPORT_CONFIG_ID))
  }
}
