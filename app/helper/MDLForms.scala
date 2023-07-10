package helper

import forms.MapMapping
import play.api.data.Form
import play.twirl.api.Html
import views.html.helper.FieldConstructor

/** Independent from the model. */
object MDLForms {
  implicit val inputFields: FieldConstructor = FieldConstructor(views.html.helpers.input.f)

  object repeatMap {

    def apply(field: play.api.data.Field, form: Form[_], defaults: List[String] = List())(
        fieldRenderer: (play.api.data.Field, String) => Html
    ): Seq[Html] = {
      val includeIndexes = MapMapping.indexes(field.name, form.data)
      val indexes = defaults.filter(!includeIndexes.contains(_)) ++ includeIndexes
      indexes.map(i => fieldRenderer(field("[" + i + "]"), i))
    }

  }

}
