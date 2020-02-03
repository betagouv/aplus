package tasks

import play.api.inject.{SimpleModule, _}

class AutoAddExpertModule
    extends SimpleModule(bind[AutoAddExpertTask].toSelf.eagerly())
