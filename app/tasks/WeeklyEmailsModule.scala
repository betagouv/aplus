package tasks

import play.api.inject.SimpleModule
import play.api.inject._

class WeeklyEmailsModule extends SimpleModule(bind[WeeklyEmailsTask].toSelf.eagerly())
