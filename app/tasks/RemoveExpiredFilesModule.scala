package tasks

import play.api.inject.{SimpleModule, _}

class RemoveExpiredFilesModule
    extends SimpleModule(bind[RemoveExpiredFilesTask].toSelf.eagerly())
