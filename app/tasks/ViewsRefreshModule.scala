package tasks

import play.api.inject.{bind, SimpleModule}

class ViewsRefreshTaskModule extends SimpleModule(bind[ViewsRefreshTask].toSelf.eagerly())
