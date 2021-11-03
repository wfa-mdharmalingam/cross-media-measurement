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

package org.wfanet.measurement.kingdom.service.api.v2alpha

import org.wfanet.measurement.api.v2alpha.ClaimReadyExchangeStepRequest
import org.wfanet.measurement.api.v2alpha.ClaimReadyExchangeStepResponse
import org.wfanet.measurement.api.v2alpha.ExchangeStep
import org.wfanet.measurement.api.v2alpha.ExchangeStepAttemptKey
import org.wfanet.measurement.api.v2alpha.ExchangeStepsGrpcKt.ExchangeStepsCoroutineImplBase
import org.wfanet.measurement.api.v2alpha.GetExchangeStepRequest
import org.wfanet.measurement.api.v2alpha.claimReadyExchangeStepResponse
import org.wfanet.measurement.common.identity.externalIdToApiId
import org.wfanet.measurement.common.toLocalDate
import org.wfanet.measurement.internal.kingdom.ExchangeStepsGrpcKt.ExchangeStepsCoroutineStub as InternalExchangeStepsCoroutineStub
import org.wfanet.measurement.internal.kingdom.claimReadyExchangeStepRequest
import org.wfanet.measurement.internal.kingdom.claimReadyExchangeStepResponse as internalClaimReadyExchangeStepResponse

class ExchangeStepsService(private val internalExchangeSteps: InternalExchangeStepsCoroutineStub) :
  ExchangeStepsCoroutineImplBase() {
  override suspend fun claimReadyExchangeStep(
    request: ClaimReadyExchangeStepRequest
  ): ClaimReadyExchangeStepResponse {
    val provider = validateRequestProvider(request.modelProvider, request.dataProvider)

    val internalRequest = claimReadyExchangeStepRequest { this.provider = provider }
    val internalResponse = internalExchangeSteps.claimReadyExchangeStep(internalRequest)
    if (internalResponse == internalClaimReadyExchangeStepResponse {}) {
      return claimReadyExchangeStepResponse {}
    }
    val externalExchangeStep = internalResponse.exchangeStep.toV2Alpha()
    val externalExchangeStepAttempt =
      ExchangeStepAttemptKey(
          recurringExchangeId =
            externalIdToApiId(internalResponse.exchangeStep.externalRecurringExchangeId),
          exchangeId = internalResponse.exchangeStep.date.toLocalDate().toString(),
          exchangeStepId = internalResponse.exchangeStep.stepIndex.toString(),
          exchangeStepAttemptId = internalResponse.attemptNumber.toString()
        )
        .toName()
    return claimReadyExchangeStepResponse {
      exchangeStep = externalExchangeStep
      exchangeStepAttempt = externalExchangeStepAttempt
    }
  }

  override suspend fun getExchangeStep(request: GetExchangeStepRequest): ExchangeStep {
    TODO("world-federation-of-advertisers/cross-media-measurement#3: implement this")
  }
}
