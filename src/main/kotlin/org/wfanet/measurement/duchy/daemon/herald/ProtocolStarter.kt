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

package org.wfanet.measurement.duchy.daemon.herald

import java.util.logging.Logger
import org.wfanet.measurement.duchy.db.computation.ComputationProtocolStageDetails
import org.wfanet.measurement.internal.duchy.ComputationToken
import org.wfanet.measurement.internal.duchy.ComputationsGrpcKt.ComputationsCoroutineStub
import org.wfanet.measurement.protocol.RequisitionKey

/**
 * Helper functions to create/start a protocol specific computation.
 */
interface ProtocolStarter {

  /** Creates an new computation. */
  suspend fun createComputation(
    globalId: String,
    computationStorageClient: ComputationsCoroutineStub,
    requisitionKeys: List<RequisitionKey>
  )

  /** Starts a computation if possible. */
  suspend fun startComputation(
    token: ComputationToken,
    computationStorageClient: ComputationsCoroutineStub,
    computationProtocolStageDetails: ComputationProtocolStageDetails,
    logger: Logger
  )
}
