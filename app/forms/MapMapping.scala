package forms

import play.api.data.{FormError, Mapping}
import play.api.data.validation.Constraint

object FormsPlusMap {

  /** Defines a repeated mapping.
    * {{{
    * Form(
    *   "name" -> map(text)
    * )
    * }}}
    *
    * @param mapping
    *   The mapping to make repeated.
    */
  def map[A](mapping: Mapping[A]): Mapping[Map[String, A]] = MapMapping(mapping)
}

object MapMapping {

  /** Computes the available indexes for the given key in this set of data.
    */
  def indexes(key: String, data: Map[String, String]): Seq[String] = {
    val KeyPattern = ("^" + java.util.regex.Pattern.quote(key) + """\[([ \p{L}0-9_-]+)\].*$""").r
    data.toSeq.collect { case (KeyPattern(index), _) => index }.sorted.distinct
  }

}

/** A mapping for repeated elements.
  *
  * @param wrapped
  *   The wrapped mapping
  */
case class MapMapping[T](
    wrapped: Mapping[T],
    key: String = "",
    constraints: Seq[Constraint[Map[String, T]]] = Nil
) extends Mapping[Map[String, T]] {

  /** The Format expected for this field, if it exists.
    */
  override val format: Option[(String, Seq[Any])] = wrapped.format

  /** Constructs a new Mapping based on this one, by adding new constraints.
    *
    * For example:
    * {{{
    *   import play.api.data._
    *   import validation.Constraints._
    *
    *   Form("phonenumber" -> text.verifying(required) )
    * }}}
    *
    * @param addConstraints
    *   the constraints to add
    * @return
    *   the new mapping
    */
  def verifying(addConstraints: Constraint[Map[String, T]]*): Mapping[Map[String, T]] =
    this.copy(constraints = constraints ++ addConstraints)

  /** Binds this field, i.e. construct a concrete value from submitted data.
    *
    * @param data
    *   the submitted data
    * @return
    *   either a concrete value of type `List[T]` or a set of errors, if the binding failed
    */
  def bind(data: Map[String, String]): Either[Seq[FormError], Map[String, T]] = {
    val allErrorsOrItems: Seq[Either[Seq[FormError], (String, T)]] =
      MapMapping.indexes(key, data).map { i =>
        wrapped.withPrefix(key + "[" + i + "]").bind(data) match {
          case Left(a)  => Left(a)
          case Right(b) => Right((i, b))
        }
      }
    if (allErrorsOrItems.forall(_.isRight)) {
      Right(allErrorsOrItems.flatMap(_.toOption).toMap).flatMap(applyConstraints)
    } else {
      Left(allErrorsOrItems.collect { case Left(errors) => errors }.flatten)
    }
  }

  /** Unbinds this field, i.e. transforms a concrete value to plain data.
    *
    * @param value
    *   the value to unbind
    * @return
    *   the plain data
    */
  def unbind(value: Map[String, T]): Map[String, String] = {
    val datas = value.map { case (k, v) => wrapped.withPrefix(key + "[" + k + "]").unbind(v) }
    datas.foldLeft(Map.empty[String, String])(_ ++ _)
  }

  /** Unbinds this field, i.e. transforms a concrete value to plain data, and applies validation.
    *
    * @param value
    *   the value to unbind
    * @return
    *   the plain data and any errors in the plain data
    */
  def unbindAndValidate(value: Map[String, T]): (Map[String, String], Seq[FormError]) = {
    val (datas, errors) = value.map { case (i, v) =>
      wrapped.withPrefix(key + "[" + i + "]").unbindAndValidate(v)
    }.unzip
    (
      datas.foldLeft(Map.empty[String, String])(_ ++ _),
      errors.flatten.toSeq ++ collectErrors(value)
    )
  }

  /** Constructs a new Mapping based on this one, adding a prefix to the key.
    *
    * @param prefix
    *   the prefix to add to the key
    * @return
    *   the same mapping, with only the key changed
    */
  def withPrefix(prefix: String): Mapping[Map[String, T]] =
    addPrefix(prefix).map(newKey => this.copy(key = newKey)).getOrElse(this)

  /** Sub-mappings (these can be seen as sub-keys).
    */
  val mappings: Seq[Mapping[_]] = wrapped.mappings
}
