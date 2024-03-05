package tasks

import play.api.inject.{bind, SimpleModule}

class AutoAddExpertModule extends SimpleModule(bind[AutoAddExpertTask].toSelf.eagerly())
