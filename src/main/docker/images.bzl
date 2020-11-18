# Copyright 2020 The Measurement System Authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Container image specs."""

# List of specs for all Docker containers to push to a container registry.
# These are common to both local execution (e.g. in Kind) as well as on GKE.
COMMON_IMAGES = [
    struct(
        name = "duchy_liquid_legions_herald_daemon_image",
        image = "//src/main/kotlin/org/wfanet/measurement/duchy/deploy/common/daemon/herald:liquid_legions_herald_daemon_image",
        repository = "ads-open-measurement/duchy/liquid-legions-v1-herald",
    ),
    struct(
        name = "duchy_liquid_legions_mill_daemon_image",
        image = "//src/main/kotlin/org/wfanet/measurement/duchy/deploy/gcloud/daemon/mill:gcs_liquid_legions_mill_daemon_image",
        repository = "ads-open-measurement/duchy/liquid-legions-v1-mill",
    ),
    struct(
        name = "duchy_publisher_data_server_image",
        image = "//src/main/kotlin/org/wfanet/measurement/duchy/deploy/common/server:publisher_data_server_image",
        repository = "ads-open-measurement/duchy/publisher-data",
    ),
    struct(
        name = "duchy_spanner_liquid_legions_computations_server_image",
        image = "//src/main/kotlin/org/wfanet/measurement/duchy/deploy/gcloud/server:spanner_liquid_legions_computations_server_image",
        repository = "ads-open-measurement/duchy/liquid-legions-v1-spanner-computations",
    ),
    struct(
        name = "kingdom_global_computation_server_image",
        image = "//src/main/kotlin/org/wfanet/measurement/kingdom/deploy/common/server:global_computation_server_image",
        repository = "ads-open-measurement/kingdom/global-computation",
    ),
    struct(
        name = "kingdom_report_maker_daemon_image",
        image = "//src/main/kotlin/org/wfanet/measurement/kingdom/deploy/common/daemon:report_maker_daemon_image",
        repository = "ads-open-measurement/kingdom/report-maker",
    ),
    struct(
        name = "kingdom_report_starter_daemon_image",
        image = "//src/main/kotlin/org/wfanet/measurement/kingdom/deploy/common/daemon:report_starter_daemon_image",
        repository = "ads-open-measurement/kingdom/report-starter",
    ),
    struct(
        name = "kingdom_requisition_linker_daemon_image",
        image = "//src/main/kotlin/org/wfanet/measurement/kingdom/deploy/common/daemon:requisition_linker_daemon_image",
        repository = "ads-open-measurement/kingdom/requisition-linker",
    ),
    struct(
        name = "kingdom_requisition_server_image",
        image = "//src/main/kotlin/org/wfanet/measurement/kingdom/deploy/common/server:requisition_server_image",
        repository = "ads-open-measurement/kingdom/requisition",
    ),
    struct(
        name = "kingdom_system_requisition_server_image",
        image = "//src/main/kotlin/org/wfanet/measurement/kingdom/deploy/common/server:system_requisition_server_image",
        repository = "ads-open-measurement/kingdom/system-requisition",
    ),
    struct(
        name = "kingdom_data_server_image",
        image = "//src/main/kotlin/org/wfanet/measurement/kingdom/deploy/gcloud/server:gcp_kingdom_data_server_image",
        repository = "ads-open-measurement/kingdom/data-server",
    ),
    struct(
        name = "setup_spanner_schema_image",
        image = "//src/main/kotlin/org/wfanet/measurement/tools:push_spanner_schema_image",
        repository = "ads-open-measurement/setup/push-spanner-schema",
    ),
]

# List of specs for all Docker containers to push to a container registry.
# These are only used on GKE.
GKE_IMAGES = [
    struct(
        name = "duchy_liquid_legions_computation_control_server_image",
        image = "//src/main/kotlin/org/wfanet/measurement/duchy/deploy/gcloud/server:gcs_liquid_legions_computation_control_server_image",
        repository = "ads-open-measurement/duchy/liquid-legions-v1-computation-control",
    ),
    struct(
        name = "gcs_correctness_test_runner_image",
        image = "//src/main/kotlin/org/wfanet/measurement/loadtest:gcs_correctness_runner_image",
        repository = "ads-open-measurement/loadtest/correctness-test",
    ),
    struct(
        name = "duchy_metric_values_server_image",
        image = "//src/main/kotlin/org/wfanet/measurement/duchy/deploy/gcloud/server:gcp_server_image",
        repository = "ads-open-measurement/duchy/metric-values",
    ),
]

# List of image build rules that are only used locally (e.g. in Kind).
LOCAL_IMAGES = [
    struct(
        name = "forwarded_storage_liquid_legions_mill_daemon_image",
        image = "//src/main/kotlin/org/wfanet/measurement/duchy/deploy/common/daemon/mill:forwarded_storage_liquid_legions_mill_daemon_image",
    ),
    struct(
        name = "forwarded_storage_liquid_legions_computation_control_server_image",
        image = "//src/main/kotlin/org/wfanet/measurement/duchy/deploy/common/server:forwarded_storage_liquid_legions_computation_control_server_image",
    ),
    struct(
        name = "spanner_forwarded_storage_server_image",
        image = "//src/main/kotlin/org/wfanet/measurement/duchy/deploy/gcloud/server:spanner_forwarded_storage_server_image",
    ),
    struct(
        name = "filesystem_storage_correctness_runner_image",
        image = "//src/main/kotlin/org/wfanet/measurement/loadtest:filesystem_storage_correctness_runner_image",
    ),
    struct(
        name = "fake_storage_server_image",
        image = "//src/main/kotlin/org/wfanet/measurement/storage/filesystem:server_image",
    ),
]

ALL_GKE_IMAGES = COMMON_IMAGES + GKE_IMAGES

ALL_LOCAL_IMAGES = COMMON_IMAGES + LOCAL_IMAGES
