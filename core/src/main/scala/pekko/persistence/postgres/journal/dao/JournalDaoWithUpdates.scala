/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package pekko.persistence.postgres.journal.dao

import org.apache.pekko.Done

import scala.concurrent.Future

/**
 * A [[JournalDao]] with extended capabilities, such as updating payloads and tags of existing events.
 * These operations should be used sparingly, for example for migrating data from un-encrypted to encrypted formats
 */
trait JournalDaoWithUpdates extends JournalDao {

  /**
   * Update (!) an existing event with the passed in data.
   */
  def update(persistenceId: String, sequenceNr: Long, payload: AnyRef): Future[Done]
}
