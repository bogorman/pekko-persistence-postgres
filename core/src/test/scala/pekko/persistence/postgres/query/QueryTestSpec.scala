/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package pekko.persistence.postgres.query

import org.apache.pekko.actor.{ ActorRef, ActorSystem, Props, Stash, Status }
import org.apache.pekko.event.LoggingReceive
import org.apache.pekko.persistence.journal.Tagged
import pekko.persistence.postgres.SingleActorSystemPerTestSpec
import pekko.persistence.postgres.query.EventAdapterTest.{ Event, TaggedAsyncEvent, TaggedEvent }
import pekko.persistence.postgres.query.javadsl.{ PostgresReadJournal => JavaPostgresReadJournal }
import pekko.persistence.postgres.query.scaladsl.PostgresReadJournal
import pekko.persistence.postgres.util.Schema.SchemaType
import org.apache.pekko.persistence.query.{ EventEnvelope, Offset, PersistenceQuery }
import org.apache.pekko.persistence.{ DeleteMessagesFailure, DeleteMessagesSuccess, PersistentActor }
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.testkit.TestSubscriber
import org.apache.pekko.stream.testkit.javadsl.{ TestSink => JavaSink }
import org.apache.pekko.stream.testkit.scaladsl.TestSink
import org.apache.pekko.stream.{ Materializer, SystemMaterializer }
import com.typesafe.config.ConfigValue
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future
import scala.concurrent.duration.{ FiniteDuration, _ }

trait ReadJournalOperations {
  def withCurrentPersistenceIds(within: FiniteDuration = 60.second)(f: TestSubscriber.Probe[String] => Unit): Unit
  def withPersistenceIds(within: FiniteDuration = 60.second)(f: TestSubscriber.Probe[String] => Unit): Unit
  def withCurrentEventsByPersistenceId(within: FiniteDuration = 60.second)(
      persistenceId: String,
      fromSequenceNr: Long = 0,
      toSequenceNr: Long = Long.MaxValue)(f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit
  def withEventsByPersistenceId(within: FiniteDuration = 60.second)(
      persistenceId: String,
      fromSequenceNr: Long = 0,
      toSequenceNr: Long = Long.MaxValue)(f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit
  def withCurrentEventsByTag(within: FiniteDuration = 60.second)(tag: String, offset: Offset)(
      f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit
  def withEventsByTag(within: FiniteDuration = 60.second)(tag: String, offset: Offset)(
      f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit
  def countJournal: Future[Long]
}

class ScalaPostgresReadJournalOperations(readJournal: PostgresReadJournal)(
    implicit system: ActorSystem,
    mat: Materializer)
    extends ReadJournalOperations {
  def this(system: ActorSystem) =
    this(PersistenceQuery(system).readJournalFor[PostgresReadJournal](PostgresReadJournal.Identifier))(
      system,
      SystemMaterializer(system).materializer)

  import system.dispatcher

  def withCurrentPersistenceIds(within: FiniteDuration)(f: TestSubscriber.Probe[String] => Unit): Unit = {
    val tp = readJournal.currentPersistenceIds().runWith(TestSink.probe[String])
    tp.within(within)(f(tp))
  }

  def withPersistenceIds(within: FiniteDuration)(f: TestSubscriber.Probe[String] => Unit): Unit = {
    val tp = readJournal.persistenceIds().runWith(TestSink.probe[String])
    tp.within(within)(f(tp))
  }

  def withCurrentEventsByPersistenceId(
      within: FiniteDuration)(persistenceId: String, fromSequenceNr: Long = 0, toSequenceNr: Long = Long.MaxValue)(
      f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit = {
    val tp = readJournal
      .currentEventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr)
      .runWith(TestSink.probe[EventEnvelope])
    tp.within(within)(f(tp))
  }

  def withEventsByPersistenceId(
      within: FiniteDuration)(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long)(
      f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit = {
    val tp = readJournal
      .eventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr)
      .runWith(TestSink.probe[EventEnvelope])
    tp.within(within)(f(tp))
  }

  def withCurrentEventsByTag(within: FiniteDuration)(tag: String, offset: Offset)(
      f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit = {
    val tp = readJournal.currentEventsByTag(tag, offset).runWith(TestSink.probe[EventEnvelope])
    tp.within(within)(f(tp))
  }

  def withEventsByTag(within: FiniteDuration)(tag: String, offset: Offset)(
      f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit = {
    val tp = readJournal.eventsByTag(tag, offset).runWith(TestSink.probe[EventEnvelope])
    tp.within(within)(f(tp))
  }

  override def countJournal: Future[Long] =
    readJournal
      .currentPersistenceIds()
      .filter(pid => (1 to 3).map(id => s"my-$id").contains(pid))
      .mapAsync(1) { pid =>
        readJournal.currentEventsByPersistenceId(pid, 0, Long.MaxValue).map(_ => 1L).runWith(Sink.seq).map(_.sum)
      }
      .runWith(Sink.seq)
      .map(_.sum)
}

class JavaDslPostgresReadJournalOperations(readJournal: javadsl.PostgresReadJournal)(
    implicit system: ActorSystem,
    mat: Materializer)
    extends ReadJournalOperations {
  def this(system: ActorSystem) =
    this(
      PersistenceQuery
        .get(system)
        .getReadJournalFor(classOf[javadsl.PostgresReadJournal], JavaPostgresReadJournal.Identifier))(
      system,
      SystemMaterializer(system).materializer)

  import system.dispatcher

  def withCurrentPersistenceIds(within: FiniteDuration)(f: TestSubscriber.Probe[String] => Unit): Unit = {
    val sink: org.apache.pekko.stream.javadsl.Sink[String, TestSubscriber.Probe[String]] = JavaSink.probe(system)
    val tp = readJournal.currentPersistenceIds().runWith(sink, mat)
    tp.within(within)(f(tp))
  }

  def withPersistenceIds(within: FiniteDuration)(f: TestSubscriber.Probe[String] => Unit): Unit = {
    val sink: org.apache.pekko.stream.javadsl.Sink[String, TestSubscriber.Probe[String]] = JavaSink.probe(system)
    val tp = readJournal.persistenceIds().runWith(sink, mat)
    tp.within(within)(f(tp))
  }

  def withCurrentEventsByPersistenceId(
      within: FiniteDuration)(persistenceId: String, fromSequenceNr: Long = 0, toSequenceNr: Long = Long.MaxValue)(
      f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit = {
    val sink: org.apache.pekko.stream.javadsl.Sink[EventEnvelope, TestSubscriber.Probe[EventEnvelope]] = JavaSink.probe(system)
    val tp = readJournal.currentEventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr).runWith(sink, mat)
    tp.within(within)(f(tp))
  }

  def withEventsByPersistenceId(
      within: FiniteDuration)(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long)(
      f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit = {
    val sink: org.apache.pekko.stream.javadsl.Sink[EventEnvelope, TestSubscriber.Probe[EventEnvelope]] = JavaSink.probe(system)
    val tp = readJournal.eventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr).runWith(sink, mat)
    tp.within(within)(f(tp))
  }

  def withCurrentEventsByTag(within: FiniteDuration)(tag: String, offset: Offset)(
      f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit = {
    val sink: org.apache.pekko.stream.javadsl.Sink[EventEnvelope, TestSubscriber.Probe[EventEnvelope]] = JavaSink.probe(system)
    val tp = readJournal.currentEventsByTag(tag, offset).runWith(sink, mat)
    tp.within(within)(f(tp))
  }

  def withEventsByTag(within: FiniteDuration)(tag: String, offset: Offset)(
      f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit = {
    val sink: org.apache.pekko.stream.javadsl.Sink[EventEnvelope, TestSubscriber.Probe[EventEnvelope]] = JavaSink.probe(system)
    val tp = readJournal.eventsByTag(tag, offset).runWith(sink, mat)
    tp.within(within)(f(tp))
  }

  override def countJournal: Future[Long] =
    readJournal
      .currentPersistenceIds()
      .asScala
      .filter(pid => (1 to 3).map(id => s"my-$id").contains(pid))
      .mapAsync(1) { pid =>
        readJournal
          .currentEventsByPersistenceId(pid, 0, Long.MaxValue)
          .asScala
          .map(_ => 1L)
          .runFold(List.empty[Long])(_ :+ _)
          .map(_.sum)
      }
      .runFold(List.empty[Long])(_ :+ _)
      .map(_.sum)
}

object QueryTestSpec {
  implicit final class EventEnvelopeProbeOps(val probe: TestSubscriber.Probe[EventEnvelope]) extends AnyVal {
    def expectNextEventEnvelope(
        persistenceId: String,
        sequenceNr: Long,
        event: Any): TestSubscriber.Probe[EventEnvelope] = {
      val env = probe.expectNext()
      assertEnvelope(env, persistenceId, sequenceNr, event)
      probe
    }

    def expectNextEventEnvelope(
        timeout: FiniteDuration,
        persistenceId: String,
        sequenceNr: Long,
        event: Any): TestSubscriber.Probe[EventEnvelope] = {
      val env = probe.expectNext(timeout)
      assertEnvelope(env, persistenceId, sequenceNr, event)
      probe
    }

    private def assertEnvelope(env: EventEnvelope, persistenceId: String, sequenceNr: Long, event: Any): Unit = {
      assert(
        env.persistenceId == persistenceId,
        s"expected persistenceId $persistenceId, found ${env.persistenceId}, in $env")
      assert(env.sequenceNr == sequenceNr, s"expected sequenceNr $sequenceNr, found ${env.sequenceNr}, in $env")
      assert(env.event == event, s"expected event $event, found ${env.event}, in $env")
    }
  }
}

abstract class QueryTestSpec(config: String, configOverrides: Map[String, ConfigValue] = Map.empty)
    extends SingleActorSystemPerTestSpec(config, configOverrides) {

  case class DeleteCmd(toSequenceNr: Long = Long.MaxValue) extends Serializable

  final val ExpectNextTimeout = 10.second

  def schemaType: SchemaType

  override def beforeAll(): Unit = {
    dropCreate(schemaType)
    super.beforeAll()
  }

  override def beforeEach(): Unit = {
    dropCreate(schemaType)
    super.beforeEach()
  }

  class TestActor(id: Int, replyToMessages: Boolean) extends PersistentActor with Stash {
    override val persistenceId: String = "my-" + id

    var state: Int = 0

    override def receiveCommand: Receive = idle

    def idle: Receive = LoggingReceive {
      case "state" =>
        sender() ! state

      case DeleteCmd(toSequenceNr) =>
        deleteMessages(toSequenceNr)
        if (replyToMessages) {
          context.become(awaitingDeleting(sender()))
        }

      case event: Int =>
        persist(event) { (event: Int) =>
          updateState(event)
          if (replyToMessages) sender() ! org.apache.pekko.actor.Status.Success(event)
        }

      case event @ Tagged(payload: Int, tags) =>
        persist(event) { (event: Tagged) =>
          updateState(payload)
          if (replyToMessages) sender() ! org.apache.pekko.actor.Status.Success((payload, tags))
        }
      case event: Event =>
        persist(event) { evt =>
          if (replyToMessages) sender() ! org.apache.pekko.actor.Status.Success(evt)
        }

      case event @ TaggedEvent(payload: Event, tag) =>
        persist(event) { evt =>
          if (replyToMessages) sender() ! org.apache.pekko.actor.Status.Success((payload, tag))
        }
      case event @ TaggedAsyncEvent(payload: Event, tag) =>
        persistAsync(event) { evt =>
          if (replyToMessages) sender() ! org.apache.pekko.actor.Status.Success((payload, tag))
        }
    }

    def awaitingDeleting(origSender: ActorRef): Receive = LoggingReceive {
      case DeleteMessagesSuccess(toSequenceNr) =>
        origSender ! s"deleted-$toSequenceNr"
        unstashAll()
        context.become(idle)

      case DeleteMessagesFailure(ex, _) =>
        origSender ! Status.Failure(ex)
        unstashAll()
        context.become(idle)

      // stash whatever other messages
      case _ => stash()
    }

    def updateState(event: Int): Unit = {
      state = state + event
    }

    override def receiveRecover: Receive = LoggingReceive { case event: Int =>
      updateState(event)
    }
  }

  def setupEmpty(persistenceId: Int, replyToMessages: Boolean)(implicit system: ActorSystem): ActorRef = {
    system.actorOf(Props(new TestActor(persistenceId, replyToMessages)))
  }

  def withTestActors(seq: Int = 1, replyToMessages: Boolean = false)(f: (ActorRef, ActorRef, ActorRef) => Unit)(
      implicit system: ActorSystem): Unit = {
    val refs = (seq until seq + 3).map(setupEmpty(_, replyToMessages)).toList
    try f(refs.head, refs.drop(1).head, refs.drop(2).head)
    finally killActors(refs: _*)
  }

  def withManyTestActors(amount: Int, seq: Int = 1, replyToMessages: Boolean = false)(f: Seq[ActorRef] => Unit)(
      implicit system: ActorSystem): Unit = {
    val refs = (seq until seq + amount).map(setupEmpty(_, replyToMessages)).toList
    try f(refs)
    finally killActors(refs: _*)
  }

  def withTags(payload: Any, tags: String*) = Tagged(payload, Set(tags: _*))

}
