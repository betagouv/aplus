package serializers

/** Contains string values shared everywhere,
  * but which need to be the same across the code.
  * eg: a name value in the view and the corresponding key in the Mapping
  */
object Keys {

  object User {
    val sharedAccount: String = "sharedAccount"
  }

}
