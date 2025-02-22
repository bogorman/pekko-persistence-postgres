/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package pekko.persistence.postgres
package query.dao

import org.apache.pekko.NotUsed
import org.apache.pekko.persistence.PersistentRepr
import pekko.persistence.postgres.journal.dao.JournalDaoWithReadMessages
import org.apache.pekko.stream.scaladsl.Source

import scala.concurrent.Future
import scala.util.Try

trait ReadJournalDao extends JournalDaoWithReadMessages {

  /**
   * Returns distinct stream of persistenceIds
   */
  def allPersistenceIdsSource(max: Long): Source[String, NotUsed]

  /**
   * Returns a Source of deserialized data for certain tag from an offset. The result is sorted by
   * the global ordering of the events.
   * Each element with be a try with a PersistentRepr, set of tags, and a Long representing the global ordering of events
   */
  def eventsByTag(tag: String, offset: Long, maxOffset: Long, max: Long): Source[Try[(PersistentRepr, Long)], NotUsed]

  /**
   * @param offset Minimum value to retrieve
   * @param limit Maximum number of values to retrieve
   * @return A Source of journal event sequence numbers (corresponding to the Ordering column)
   */
  def journalSequence(offset: Long, limit: Long): Source[Long, NotUsed]

  /**
   * @return The value of the maximum (ordering) id in the journal
   */
  def maxJournalSequence(): Future[Long]
}
