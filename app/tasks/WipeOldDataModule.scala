package tasks

import play.api.inject.{SimpleModule, _}

class WipeOldDataModule extends SimpleModule(bind[WipeOldDataTask].toSelf.eagerly())
