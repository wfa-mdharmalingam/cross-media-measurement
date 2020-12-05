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

#ifndef WFA_MEASUREMENT_COMMON_CRYPTO_PROTOCAL_ENCRYPTION_UTILITY_WRAPPER_H_
#define WFA_MEASUREMENT_COMMON_CRYPTO_PROTOCAL_ENCRYPTION_UTILITY_WRAPPER_H_

#include "absl/status/statusor.h"

// Wrapper methods used to generate the swig/JNI Java classes.
// The only functionality of these methods are converting between proto messages
// and their corresponding serialized strings, and then calling into the
// protocol_encryption_utility methods.
namespace wfa {
namespace measurement {
namespace common {
namespace crypto {

absl::StatusOr<std::string> AddNoiseToSketch(
    const std::string& serialized_request);

absl::StatusOr<std::string> BlindOneLayerRegisterIndex(
    const std::string& serialized_request);

absl::StatusOr<std::string> BlindLastLayerIndexThenJoinRegisters(
    const std::string& serialized_request);

absl::StatusOr<std::string> DecryptOneLayerFlagAndCount(
    const std::string& serialized_request);

absl::StatusOr<std::string> DecryptLastLayerFlagAndCount(
    const std::string& serialized_request);

}  // namespace crypto
}  // namespace common
}  // namespace measurement
}  // namespace wfa

#endif  // WFA_MEASUREMENT_COMMON_CRYPTO_PROTOCAL_ENCRYPTION_UTILITY_WRAPPER_H_
