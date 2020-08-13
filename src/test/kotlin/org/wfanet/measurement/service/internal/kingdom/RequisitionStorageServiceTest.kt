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

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.extensions.proto.ProtoTruth.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import java.time.Instant
import kotlin.test.assertFails
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.wfanet.measurement.common.ExternalId
import org.wfanet.measurement.db.kingdom.KingdomRelationalDatabase
import org.wfanet.measurement.db.kingdom.streamRequisitionsFilter
import org.wfanet.measurement.internal.kingdom.FulfillRequisitionRequest
import org.wfanet.measurement.internal.kingdom.Requisition
import org.wfanet.measurement.internal.kingdom.Requisition.RequisitionState
import org.wfanet.measurement.internal.kingdom.RequisitionStorageGrpcKt.RequisitionStorageCoroutineStub
import org.wfanet.measurement.internal.kingdom.StreamRequisitionsRequest
import org.wfanet.measurement.service.testing.GrpcTestServerRule

@RunWith(JUnit4::class)
class RequisitionStorageServiceTest {

  companion object {
    val REQUISITION: Requisition = Requisition.newBuilder().apply {
      externalDataProviderId = 1
      externalCampaignId = 2
      externalRequisitionId = 3
      createTimeBuilder.seconds = 456
      state = RequisitionState.UNFULFILLED
    }.build()
  }

  private val kingdomRelationalDatabase: KingdomRelationalDatabase = mock() {
    onBlocking { writeNewRequisition(any()) }.thenReturn(REQUISITION)
    onBlocking { fulfillRequisition(any(), any()) }.thenReturn(REQUISITION)
    on { streamRequisitions(any(), any()) }.thenReturn(flowOf(REQUISITION, REQUISITION))
  }

  @get:Rule
  val grpcTestServerRule = GrpcTestServerRule {
    listOf(RequisitionStorageService(kingdomRelationalDatabase))
  }

  private val stub by lazy { RequisitionStorageCoroutineStub(grpcTestServerRule.channel) }

  @Test
  fun `createRequisition fails with id`() = runBlocking<Unit> {
    val requisition: Requisition = Requisition.newBuilder().apply {
      externalDataProviderId = 1
      externalCampaignId = 2
      externalRequisitionId = 3 // <-- should be unset
      state = RequisitionState.UNFULFILLED
    }.build()

    assertFails { stub.createRequisition(requisition) }
  }

  @Test
  fun `createRequisition fails with wrong state`() = runBlocking<Unit> {
    val requisition: Requisition = Requisition.newBuilder().apply {
      externalDataProviderId = 1
      externalCampaignId = 2
      state = RequisitionState.FULFILLED
    }.build()

    assertFails { stub.createRequisition(requisition) }
  }

  @Test
  fun `createRequisition success`() = runBlocking<Unit> {
    val inputRequisition: Requisition = Requisition.newBuilder().apply {
      externalDataProviderId = 1
      externalCampaignId = 2
      state = RequisitionState.UNFULFILLED
    }.build()

    assertThat(stub.createRequisition(inputRequisition))
      .isEqualTo(REQUISITION)

    verify(kingdomRelationalDatabase)
      .writeNewRequisition(inputRequisition)
  }

  @Test
  fun fulfillRequisition() = runBlocking<Unit> {
    val request: FulfillRequisitionRequest =
      FulfillRequisitionRequest.newBuilder()
        .setExternalRequisitionId(12345)
        .setDuchyId("some-duchy")
        .build()

    assertThat(stub.fulfillRequisition(request))
      .isEqualTo(REQUISITION)

    verify(kingdomRelationalDatabase)
      .fulfillRequisition(ExternalId(12345), "some-duchy")
  }

  @Test
  fun streamRequisitions() = runBlocking<Unit> {
    val request: StreamRequisitionsRequest =
      StreamRequisitionsRequest.newBuilder().apply {
        limit = 10
        filterBuilder.apply {
          addExternalDataProviderIds(1)
          addExternalDataProviderIds(2)
          addStates(RequisitionState.FULFILLED)
          createdAfterBuilder.seconds = 12345
        }
      }.build()

    assertThat(stub.streamRequisitions(request).toList())
      .containsExactly(REQUISITION, REQUISITION)

    val expectedFilter = streamRequisitionsFilter(
      externalDataProviderIds = listOf(ExternalId(1), ExternalId(2)),
      states = listOf(RequisitionState.FULFILLED),
      createdAfter = Instant.ofEpochSecond(12345)
    )

    verify(kingdomRelationalDatabase)
      .streamRequisitions(
        check { assertThat(it.clauses).containsExactlyElementsIn(expectedFilter.clauses) },
        eq(10)
      )
  }
}
