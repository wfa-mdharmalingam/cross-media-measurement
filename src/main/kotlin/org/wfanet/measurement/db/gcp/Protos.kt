package org.wfanet.measurement.db.gcp

import com.google.cloud.ByteArray
import com.google.protobuf.Message
import com.google.protobuf.Parser

/** Converts a protobuf [Message] to a spanner [ByteArray]. */
fun Message.toGcpByteArray(): ByteArray {
  return ByteArray.copyFrom(this.toByteArray())
}

/** Parse a spanner [ByteArray] into a protobuf [Message]. */
fun <T : Message> ByteArray.toProtoMessage(parser: Parser<T>): T {
  return parser.parseFrom(this.toByteArray())
}
