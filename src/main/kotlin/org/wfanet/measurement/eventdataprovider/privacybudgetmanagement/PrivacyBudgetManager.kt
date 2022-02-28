<<<<<<< HEAD
/**
 * Copyright 2022 The Cross-Media Measurement Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * ```
 *      http://www.apache.org/licenses/LICENSE-2.0
 * ```
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
=======
// Copyright 2022 The Cross-Media Measurement Authors
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

>>>>>>> 637139dc (Adds skeleton of privacy budget manager)
package org.wfanet.measurement.eventdataprovider.privacybudgetmanagement

import org.wfanet.measurement.api.v2alpha.MeasurementSpec
import org.wfanet.measurement.api.v2alpha.RequisitionSpec

/**
 * This is the default value for the total amount that can be charged to a single privacy bucket.
 */
object PbmConstants {
  const val MAXIMUM_PRIVACY_USAGE_PER_BUCKET = 1.0f
}

/**
 * Instantiates a privacy budget manager.
 *
 * @param backingStore: An object that provides persistent storage of privacy budget data in a
 * consistent and atomic manner.
 * @param maximumPrivacyBudget: The maximum privacy budget that can be used in any privacy bucket.
 */
class PrivacyBudgetManager(
  val backingStore: PrivacyBudgetLedgerBackingStore,
  val maximumPrivacyBudget: Float = PbmConstants.MAXIMUM_PRIVACY_USAGE_PER_BUCKET
) {

  /**
   * Charges all of the privacy buckets identified by the given measurementSpec and requisitionSpec,
<<<<<<< HEAD
   * if possible.
=======
   * if possible. If successful, returns a PrivacyBudgetReturnStatus with null errorMessage. If one
   * or more of the buckets would be exceeded, then does not charge any bucket. In this case,
   * returns a PrivacyBudgetManagerReturnStatus object with an error message and list of buckets
   * that would overflow.
>>>>>>> 637139dc (Adds skeleton of privacy budget manager)
   *
   * @param requisitionSpec The requisitionSpec protobuf that is associated with the query. The date
   * range and demo groups are obtained from this.
   * @param measurementSpec The measurementSpec protobuf that is associated with the query. The VID
   * sampling interval is obtained from from this.
<<<<<<< HEAD
   * @throws PrivacyBudgetManagerException if an error occurs in handling this request. Possible
   * exceptions could include running out of privacy budget or a failure to commit the transaction
   * to the database.
=======
>>>>>>> 637139dc (Adds skeleton of privacy budget manager)
   */
  fun chargePrivacyBudget(
    requisitionSpec: RequisitionSpec,
    measurementSpec: MeasurementSpec
<<<<<<< HEAD
  ): Unit = TODO("not implemented $requisitionSpec $measurementSpec")
=======
  ): PrivacyBudgetManagerReturnStatus {
<<<<<<< HEAD
    throw RuntimeException("not implemented ${requisitionSpec} ${measurementSpec}")
  }
>>>>>>> 637139dc (Adds skeleton of privacy budget manager)
=======
    TODO("not implemented ${requisitionSpec} ${measurementSpec}")
<<<<<<< HEAD
	}
>>>>>>> c42dc22c (Changes in response to comments from Steven Ware Jones)
=======
  }
>>>>>>> 3ae47909 (Fix errors reported by formatter)
}
