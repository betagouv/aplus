package tasks

import play.api.inject.{bind, SimpleModule}

class WeeklyEmailsModule extends SimpleModule(bind[WeeklyEmailsTask].toSelf.eagerly())
