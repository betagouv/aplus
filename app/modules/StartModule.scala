package modules

import com.google.inject.AbstractModule
import io.sentry.Sentry
import javax.inject.{Inject, Singleton}
import play.api.Configuration

class StartModule extends AbstractModule {

  override def configure(): Unit =
    bind(classOf[ApplicationStart]).asEagerSingleton()

}

// See documentation here:
// https://www.playframework.com/documentation/2.8.x/ScalaDependencyInjection#Eager-bindings
@Singleton
class ApplicationStart @Inject() (configuration: Configuration) {

  val dsn: String = configuration.getOptional[String]("sentry.dsn").getOrElse("")
  val tracesSampleRate: Double = configuration.get[Double]("sentry.tracesSampleRate")

  Sentry.init { options =>
    options.setDsn(dsn)
    options.setTracesSampleRate(tracesSampleRate)
  }

}
