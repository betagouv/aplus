package views

import modules.AppConfig

/** Contains infos that we want to pass to all views. */
case class MainInfos(isDemo: Boolean, config: AppConfig)
