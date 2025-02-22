package pekko.persistence.postgres.snapshot.dao

import pekko.persistence.postgres.config.SnapshotConfig
import pekko.persistence.postgres.snapshot.dao.SnapshotTables.SnapshotRow
import pekko.persistence.postgres.util.BaseQueryTest
import io.circe.{ Json, JsonObject }

class SnapshotQueriesTest extends BaseQueryTest {
  import pekko.persistence.postgres.db.ExtendedPostgresProfile.api._

  it should "create SQL query for selectAllByPersistenceId.delete" in withSnapshotQueries { queries =>
    queries
      .selectAllByPersistenceId("p1")
      .delete shouldBeSQL """delete from "snapshot" where "snapshot"."persistence_id" = ?"""
  }

  it should "create SQL query for selectAllByPersistenceId" in withSnapshotQueries { queries =>
    queries.selectAllByPersistenceId(
      "p1") shouldBeSQL """select "persistence_id", "sequence_number", "created", "snapshot", "metadata" from "snapshot" where "persistence_id" = ? order by "sequence_number" desc"""
  }

  it should "create SQL query for insertOrUpdate" in withSnapshotQueries { queries =>
    queries.insertOrUpdate(
      SnapshotRow(
        "p1",
        32L,
        1333L,
        Array.ofDim(0),
        emptyJson)) shouldBeSQL """insert into "snapshot" ("persistence_id","sequence_number","created","snapshot","metadata") values (?,?,?,?,?) on conflict ("persistence_id", "sequence_number") do update set "created"=EXCLUDED."created","snapshot"=EXCLUDED."snapshot","metadata"=EXCLUDED."metadata""""
  }

  it should "create SQL query for selectLatestByPersistenceId" in withSnapshotQueries { queries =>
    queries.selectLatestByPersistenceId(
      "p1") shouldBeSQL """select "persistence_id", "sequence_number", "created", "snapshot", "metadata" from "snapshot" where "persistence_id" = ? order by "sequence_number" desc limit 1"""
  }

  it should "create SQL query for selectByPersistenceIdAndSequenceNr" in withSnapshotQueries { queries =>
    queries.selectByPersistenceIdAndSequenceNr(
      "p1",
      11L) shouldBeSQL """select "persistence_id", "sequence_number", "created", "snapshot", "metadata" from "snapshot" where ("persistence_id" = ?) and ("sequence_number" = ?) order by "sequence_number" desc"""
  }

  it should "create SQL query for selectByPersistenceIdAndSequenceNr.delete" in withSnapshotQueries { queries =>
    queries
      .selectByPersistenceIdAndSequenceNr("p1", 11L)
      .delete shouldBeSQL """delete from "snapshot" where ("snapshot"."persistence_id" = ?) and ("snapshot"."sequence_number" = ?)"""
  }

  it should "create SQL query for selectByPersistenceIdUpToMaxTimestamp" in withSnapshotQueries { queries =>
    queries.selectByPersistenceIdUpToMaxTimestamp(
      "p1",
      11L) shouldBeSQL """select "persistence_id", "sequence_number", "created", "snapshot", "metadata" from "snapshot" where ("persistence_id" = ?) and ("created" <= ?) order by "sequence_number" desc"""
  }

  it should "create SQL query for selectByPersistenceIdUpToMaxTimestamp.delete" in withSnapshotQueries { queries =>
    queries
      .selectByPersistenceIdUpToMaxTimestamp("p1", 11L)
      .delete shouldBeSQL """delete from "snapshot" where ("snapshot"."persistence_id" = ?) and ("snapshot"."created" <= ?)"""
  }

  it should "create SQL query for selectByPersistenceIdUpToMaxSequenceNr" in withSnapshotQueries { queries =>
    queries.selectByPersistenceIdUpToMaxSequenceNr(
      "p1",
      11L) shouldBeSQL """select "persistence_id", "sequence_number", "created", "snapshot", "metadata" from "snapshot" where ("persistence_id" = ?) and ("sequence_number" <= ?) order by "sequence_number" desc"""
  }

  it should "create SQL query for selectByPersistenceIdUpToMaxSequenceNr.delete" in withSnapshotQueries { queries =>
    queries
      .selectByPersistenceIdUpToMaxSequenceNr("p1", 11L)
      .delete shouldBeSQL """delete from "snapshot" where ("snapshot"."persistence_id" = ?) and ("snapshot"."sequence_number" <= ?)"""
  }

  it should "create SQL query for selectByPersistenceIdUpToMaxSequenceNrAndMaxTimestamp" in withSnapshotQueries {
    queries =>
      queries.selectByPersistenceIdUpToMaxSequenceNrAndMaxTimestamp(
        "p1",
        11L,
        23L) shouldBeSQL """select "persistence_id", "sequence_number", "created", "snapshot", "metadata" from "snapshot" where (("persistence_id" = ?) and ("sequence_number" <= ?)) and ("created" <= ?) order by "sequence_number" desc"""
  }

  it should "create SQL query for selectByPersistenceIdUpToMaxSequenceNrAndMaxTimestamp.delete" in withSnapshotQueries {
    queries =>
      queries
        .selectByPersistenceIdUpToMaxSequenceNrAndMaxTimestamp("p1", 11L, 23L)
        .delete shouldBeSQL """delete from "snapshot" where (("snapshot"."persistence_id" = ?) and ("snapshot"."sequence_number" <= ?)) and ("snapshot"."created" <= ?)"""
  }

  it should "create SQL query for selectOneByPersistenceIdAndMaxTimestamp" in withSnapshotQueries { queries =>
    queries.selectOneByPersistenceIdAndMaxTimestamp(
      "p1",
      11L) shouldBeSQL """select "persistence_id", "sequence_number", "created", "snapshot", "metadata" from "snapshot" where ("persistence_id" = ?) and ("created" <= ?) order by "sequence_number" desc limit 1"""
  }

  it should "create SQL query for selectOneByPersistenceIdAndMaxSequenceNr" in withSnapshotQueries { queries =>
    queries.selectOneByPersistenceIdAndMaxSequenceNr(
      "p1",
      23L) shouldBeSQL """select "persistence_id", "sequence_number", "created", "snapshot", "metadata" from "snapshot" where ("persistence_id" = ?) and ("sequence_number" <= ?) order by "sequence_number" desc limit 1"""
  }

  it should "create SQL query for selectOneByPersistenceIdAndMaxSequenceNrAndMaxTimestamp" in withSnapshotQueries {
    queries =>
      queries.selectOneByPersistenceIdAndMaxSequenceNrAndMaxTimestamp(
        "p1",
        11L,
        23L) shouldBeSQL """select "persistence_id", "sequence_number", "created", "snapshot", "metadata" from "snapshot" where (("persistence_id" = ?) and ("sequence_number" <= ?)) and ("created" <= ?) order by "sequence_number" desc limit 1"""
  }

  lazy val emptyJson: Json = Json.fromJsonObject(JsonObject.empty)

  private def withSnapshotQueries(f: SnapshotQueries => Unit): Unit = {
    withActorSystem { implicit system =>
      f(new SnapshotQueries(new SnapshotConfig(cfg).snapshotTableConfiguration))
    }
  }
}
