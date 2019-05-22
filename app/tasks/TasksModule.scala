package tasks

import play.api.inject.SimpleModule
import play.api.inject._

class TasksModule extends SimpleModule(bind[ExpertTask].toSelf.eagerly())
