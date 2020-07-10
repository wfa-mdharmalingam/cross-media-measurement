package org.wfanet.measurement.db.duchy.gcp

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.contrib.nio.testing.LocalStorageHelper
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.wfanet.measurement.common.DuchyRole
import org.wfanet.measurement.db.duchy.BlobRef
import org.wfanet.measurement.db.duchy.ComputationToken
import org.wfanet.measurement.internal.SketchAggregationStage

@RunWith(JUnit4::class)
class GcpStorageComputationsDbTest {
  private lateinit var blobsDb: GcpStorageComputationsDb<SketchAggregationStage>
  companion object {
    const val TEST_BUCKET = "testing-bucket"
    private val storage: Storage = LocalStorageHelper.getOptions().service
    private val token = ComputationToken(
      localId = 5432L, globalId = 6789, attempt = 1, lastUpdateTime = 1234567891011L,
      stage = SketchAggregationStage.TO_DECRYPT_FLAG_COUNTS, nextWorker = "next-one", owner = "me",
      role = DuchyRole.PRIMARY
    )
  }

  @Before
  fun setUp() {
    blobsDb = GcpStorageComputationsDb<SketchAggregationStage>(storage, TEST_BUCKET)
  }

  @Test
  fun readBlob() = runBlocking<Unit> {
    val blobData = "Somedata".toByteArray()
    storage.create(BlobInfo.newBuilder(BlobId.of(TEST_BUCKET, "path")).build(), blobData)
    val data = blobsDb.read(BlobRef(1234L, "path"))
    assertThat(data).isEqualTo(blobData)
  }

  @Test
  fun `readBlob fails when blob is missing`() = runBlocking<Unit> {
    assertFailsWith<IllegalStateException> {
      blobsDb.read(BlobRef(1234L, "path/to/unwritten/blob"))
    }
  }

  @Test
  fun newPath() = runBlocking<Unit> {
    val pathWithRandomSuffix = blobsDb.newBlobPath(token, "finished_sketch")
    assertThat(pathWithRandomSuffix).startsWith("5432/TO_DECRYPT_FLAG_COUNTS/finished_sketch")
    val secondPathWithRandomSuffix = blobsDb.newBlobPath(token, "finished_sketch")
    assertThat(pathWithRandomSuffix).startsWith("5432/TO_DECRYPT_FLAG_COUNTS/finished_sketch")
    assertThat(pathWithRandomSuffix).isNotEqualTo(secondPathWithRandomSuffix)
  }

  @Test
  fun writeBlobPath() = runBlocking<Unit> {
    val pathToBlob = "path/to/a/blob"
    val blobData = "data-to-write-to-storage".toByteArray()
    blobsDb.blockingWrite(pathToBlob, blobData)
    assertThat(storage[BlobId.of(TEST_BUCKET, pathToBlob)].getContent()).isEqualTo(blobData)
  }

  @Test
  fun `blob lifecycle`() = runBlocking {
    val blob1 = "abcdefghijklmnopqrstuvwxyz".toByteArray()
    val blob2 = "123456789011121314151617181920".toByteArray()
    val blob1Path = blobsDb.newBlobPath(token, "my-new-blob")
    val blob1Ref = BlobRef(0L, blob1Path)
    val blob2Path = blobsDb.newBlobPath(token, "some-other-blob")
    val blob2Ref = BlobRef(1L, blob2Path)
    // Write both blobs using the implementation.
    blobsDb.blockingWrite(blob1Ref, blob1)
    blobsDb.blockingWrite(blob2Ref, blob2)
    // Read both blobs making sure they are as expected.
    assertThat(blobsDb.read(blob1Ref)).isEqualTo(blob1)
    assertThat(blobsDb.read(blob2Ref)).isEqualTo(blob2)
    // Delete one of the blobs
    blobsDb.delete(blob1Ref)
    // Make sure the delted blob is no longer present, and the other one is still there.
    assertFailsWith<IllegalStateException> { blobsDb.read(blob1Ref) }
    assertThat(blobsDb.read(blob2Ref)).isEqualTo(blob2)
  }
}
