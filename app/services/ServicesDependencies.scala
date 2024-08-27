package services

import cats.effect.unsafe.IORuntime
import javax.inject.{Inject, Singleton}
import org.apache.pekko.actor.ActorSystem
import play.api.inject.ApplicationLifecycle
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ServicesDependencies @Inject() (
    actorSystem: ActorSystem,
    lifecycle: ApplicationLifecycle,
) {

  /** Gives access to an ExecutionContext for the DB queries, that are supposed to be blocking.
    *
    * https://www.playframework.com/documentation/2.8.x/ThreadPools#Many-specific-thread-pools
    */
  implicit val databaseExecutionContext: ExecutionContext =
    actorSystem.dispatchers.lookup("contexts.blocking-db-queries")

  implicit val mailerExecutionContext: ExecutionContext =
    actorSystem.dispatchers.lookup("contexts.blocking-mailer-connections")

  /** https://github.com/typelevel/cats-effect/blob/v3.3.14/core/jvm/src/main/scala/cats/effect/unsafe/IORuntimeCompanionPlatform.scala#L158
    */
  implicit val ioRuntime: IORuntime =
    cats.effect.unsafe.IORuntime.global

  lifecycle.addStopHook { () =>
    ioRuntime.shutdown()
    Future.successful(())
  }

}
