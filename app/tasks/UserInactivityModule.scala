package tasks

import play.api.inject.{bind, SimpleModule}

class UserInactivityTaskModule extends SimpleModule(bind[UserInactivityTask].toSelf.eagerly())
