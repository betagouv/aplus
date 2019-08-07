package tasks

import play.api.inject.SimpleModule
import play.api.inject._

class AutoAddExpertModule extends SimpleModule(bind[AutoAddExpertTask].toSelf.eagerly())
