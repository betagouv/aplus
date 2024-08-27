package tasks

import play.api.inject.{bind, SimpleModule}

class ExportAnonymizedDataModule
    extends SimpleModule(bind[ExportAnonymizedDataTask].toSelf.eagerly())
