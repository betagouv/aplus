package tasks

import play.api.inject.{SimpleModule, _}

class RekeyEncryptedFieldsModule
    extends SimpleModule(bind[RekeyEncryptedFieldsTask].toSelf.eagerly())
