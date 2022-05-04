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
package org.wfanet.measurement.eventdataprovider.privacybudgetmanagement

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AdvancedCompositionTest {
  @Test
  fun `advanced composition computation works as expected`() {
    assertThat(
        AdvancedComposition.totalPrivacyBudgetUsageUnderAdvancedComposition(
          PrivacyCharge(1.0f, 0.0f),
          30,
          0.0f
        )
      )
      .isEqualTo(30.0f)
    assertThat(
        AdvancedComposition.totalPrivacyBudgetUsageUnderAdvancedComposition(
          PrivacyCharge(1.0f, 0.001f),
          30,
          0.06f
        )
      )
      .isEqualTo(22.0f)
    assertThat(
        AdvancedComposition.totalPrivacyBudgetUsageUnderAdvancedComposition(
          PrivacyCharge(1.0f, 0.001f),
          30,
          0.1f
        )
      )
      .isEqualTo(20.0f)
    assertThat(
        AdvancedComposition.totalPrivacyBudgetUsageUnderAdvancedComposition(
          PrivacyCharge(1.0f, 0.2f),
          1,
          0.1f
        )
      )
      .isEqualTo(null)
    assertThat(
        AdvancedComposition.totalPrivacyBudgetUsageUnderAdvancedComposition(
          PrivacyCharge(1.0f, 0.01f),
          30,
          0.26f
        )
      )
      .isEqualTo(null)
  }
}
