/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package pekko.persistence.postgres.serialization

import org.apache.pekko.persistence.SnapshotMetadata

import scala.util.Try

trait SnapshotSerializer[T] {
  def serialize(metadata: SnapshotMetadata, snapshot: Any): Try[T]

  def deserialize(t: T): Try[(SnapshotMetadata, Any)]
}
