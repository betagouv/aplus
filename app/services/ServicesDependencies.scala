package services

import akka.actor.ActorSystem
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ServicesDependencies @Inject() (
    actorSystem: ActorSystem
) {

  /** Gives access to an ExecutionContext for the DB queries, that are
    * supposed to be blocking.
    *
    * https://www.playframework.com/documentation/2.8.x/ThreadPools#Many-specific-thread-pools
    */
  implicit val databaseExecutionContext: ExecutionContext =
    actorSystem.dispatchers.lookup("contexts.blocking-db-queries")

}
