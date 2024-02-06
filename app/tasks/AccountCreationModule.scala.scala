package tasks

import play.api.inject.{bind, SimpleModule}

class AccountCreationModule extends SimpleModule(bind[AccountCreationTask].toSelf.eagerly())
