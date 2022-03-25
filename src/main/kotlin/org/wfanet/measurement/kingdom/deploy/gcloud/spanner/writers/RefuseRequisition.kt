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

package org.wfanet.measurement.kingdom.deploy.gcloud.spanner.writers

import org.wfanet.measurement.common.identity.externalIdToApiId
import org.wfanet.measurement.internal.kingdom.ErrorCode
import org.wfanet.measurement.internal.kingdom.Measurement
import org.wfanet.measurement.internal.kingdom.MeasurementKt
import org.wfanet.measurement.internal.kingdom.RefuseRequisitionRequest
import org.wfanet.measurement.internal.kingdom.Requisition
import org.wfanet.measurement.internal.kingdom.copy
import org.wfanet.measurement.kingdom.deploy.gcloud.spanner.common.KingdomInternalException
import org.wfanet.measurement.kingdom.deploy.gcloud.spanner.readers.RequisitionReader

/**
 * Refuses a [Requisition].
 *
 * Throws a [KingdomInternalException] on [execute] with the following codes/conditions:
 * * [ErrorCode.MEASUREMENT_STATE_ILLEGAL]
 * * [ErrorCode.REQUISITION_STATE_ILLEGAL]
 * * [ErrorCode.REQUISITION_NOT_FOUND]
 */
class RefuseRequisition(private val request: RefuseRequisitionRequest) :
  SpannerWriter<Requisition, Requisition>() {
  override suspend fun TransactionScope.runTransaction(): Requisition {
    val readResult: RequisitionReader.Result = readRequisition()
    val (measurementConsumerId, measurementId, _, requisition, measurementDetails) = readResult

    val state = requisition.state
    if (state != Requisition.State.UNFULFILLED) {
      throw KingdomInternalException(ErrorCode.REQUISITION_STATE_ILLEGAL) {
        "Expected ${Requisition.State.UNFULFILLED}, got $state"
      }
    }
    val measurementState = requisition.parentMeasurement.state
    if (measurementState != Measurement.State.PENDING_REQUISITION_FULFILLMENT) {
      throw KingdomInternalException(ErrorCode.MEASUREMENT_STATE_ILLEGAL) {
        "Expected ${Measurement.State.PENDING_REQUISITION_FULFILLMENT}, got $measurementState"
      }
    }

    val updatedDetails = requisition.details.copy { refusal = request.refusal }
    val updatedMeasurementDetails =
      measurementDetails.copy {
        failure =
          MeasurementKt.failure {
            reason = Measurement.Failure.Reason.REQUISITION_REFUSED
            message =
              "ID of refused Requisition: " + externalIdToApiId(request.externalRequisitionId)
          }
      }
    updateRequisition(readResult, Requisition.State.REFUSED, updatedDetails)
    updateMeasurementState(
      measurementConsumerId,
      measurementId,
      Measurement.State.FAILED,
      updatedMeasurementDetails
    )

    return requisition.copy {
      this.state = Requisition.State.REFUSED
      details = updatedDetails
      parentMeasurement = parentMeasurement.copy { this.state = Measurement.State.FAILED }
    }
  }

  override fun ResultScope<Requisition>.buildResult(): Requisition {
    return checkNotNull(transactionResult).copy { updateTime = commitTimestamp.toProto() }
  }

  private suspend fun TransactionScope.readRequisition(): RequisitionReader.Result {
    val externalDataProviderId = request.externalDataProviderId
    val externalRequisitionId = request.externalRequisitionId

    val readResult: RequisitionReader.Result =
      RequisitionReader()
        .readByExternalDataProviderId(
          transactionContext,
          externalDataProviderId = externalDataProviderId,
          externalRequisitionId = externalRequisitionId
        )
        ?: throw KingdomInternalException(ErrorCode.REQUISITION_NOT_FOUND) {
          "Requisition with external DataProvider ID $externalDataProviderId and external " +
            "Requisition ID $externalRequisitionId not found"
        }
    return readResult
  }
}
