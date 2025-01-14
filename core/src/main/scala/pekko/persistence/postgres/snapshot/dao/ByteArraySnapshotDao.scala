/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package pekko.persistence.postgres.snapshot.dao

import org.apache.pekko.persistence.SnapshotMetadata
import pekko.persistence.postgres.config.SnapshotConfig
import pekko.persistence.postgres.snapshot.dao.SnapshotTables.SnapshotRow
import org.apache.pekko.serialization.Serialization
import org.apache.pekko.stream.Materializer
import slick.jdbc.JdbcProfile
import slick.jdbc.JdbcBackend

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

class ByteArraySnapshotDao(db: JdbcBackend#Database, snapshotConfig: SnapshotConfig, serialization: Serialization)(
    implicit ec: ExecutionContext,
    val mat: Materializer)
    extends SnapshotDao {
  import pekko.persistence.postgres.db.ExtendedPostgresProfile.api._

  val queries = new SnapshotQueries(snapshotConfig.snapshotTableConfiguration)

  val serializer = new ByteArraySnapshotSerializer(serialization)

  def toSnapshotData(row: SnapshotRow): (SnapshotMetadata, Any) =
    serializer.deserialize(row) match {
      case Success(deserialized) => deserialized
      case Failure(cause)        => throw cause
    }

  override def latestSnapshot(persistenceId: String): Future[Option[(SnapshotMetadata, Any)]] =
    for {
      rows <- db.run(queries.selectLatestByPersistenceId(persistenceId).result)
    } yield rows.headOption.map(toSnapshotData)

  override def snapshotForMaxTimestamp(
      persistenceId: String,
      maxTimestamp: Long): Future[Option[(SnapshotMetadata, Any)]] =
    for {
      rows <- db.run(queries.selectOneByPersistenceIdAndMaxTimestamp(persistenceId, maxTimestamp).result)
    } yield rows.headOption.map(toSnapshotData)

  override def snapshotForMaxSequenceNr(
      persistenceId: String,
      maxSequenceNr: Long): Future[Option[(SnapshotMetadata, Any)]] =
    for {
      rows <- db.run(queries.selectOneByPersistenceIdAndMaxSequenceNr(persistenceId, maxSequenceNr).result)
    } yield rows.headOption.map(toSnapshotData)

  override def snapshotForMaxSequenceNrAndMaxTimestamp(
      persistenceId: String,
      maxSequenceNr: Long,
      maxTimestamp: Long): Future[Option[(SnapshotMetadata, Any)]] =
    for {
      rows <- db.run(
        queries
          .selectOneByPersistenceIdAndMaxSequenceNrAndMaxTimestamp(persistenceId, maxSequenceNr, maxTimestamp)
          .result)
    } yield rows.headOption.map(toSnapshotData)

  override def save(snapshotMetadata: SnapshotMetadata, snapshot: Any): Future[Unit] = {
    val eventualSnapshotRow = Future.fromTry(serializer.serialize(snapshotMetadata, snapshot))
    eventualSnapshotRow.map(queries.insertOrUpdate).flatMap(db.run).map(_ => ())
  }

  override def delete(persistenceId: String, sequenceNr: Long): Future[Unit] =
    for {
      _ <- db.run(queries.selectByPersistenceIdAndSequenceNr(persistenceId, sequenceNr).delete)
    } yield ()

  override def deleteAllSnapshots(persistenceId: String): Future[Unit] =
    for {
      _ <- db.run(queries.selectAllByPersistenceId(persistenceId).delete)
    } yield ()

  override def deleteUpToMaxSequenceNr(persistenceId: String, maxSequenceNr: Long): Future[Unit] =
    for {
      _ <- db.run(queries.selectByPersistenceIdUpToMaxSequenceNr(persistenceId, maxSequenceNr).delete)
    } yield ()

  override def deleteUpToMaxTimestamp(persistenceId: String, maxTimestamp: Long): Future[Unit] =
    for {
      _ <- db.run(queries.selectByPersistenceIdUpToMaxTimestamp(persistenceId, maxTimestamp).delete)
    } yield ()

  override def deleteUpToMaxSequenceNrAndMaxTimestamp(
      persistenceId: String,
      maxSequenceNr: Long,
      maxTimestamp: Long): Future[Unit] =
    for {
      _ <- db.run(
        queries
          .selectByPersistenceIdUpToMaxSequenceNrAndMaxTimestamp(persistenceId, maxSequenceNr, maxTimestamp)
          .delete)
    } yield ()
}
