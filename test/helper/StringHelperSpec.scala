package helper

import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class StringHelperSpec extends Specification {

  "String Helper should" >> {
    "capitalize simple name" >> {
      val name = "mAtHiEu"
      StringHelper.capitalizeFirstName(name) must equalTo("Mathieu")
    }

    "capitalize dash composed name" >> {
      val name = "JeAn-rEné"
      StringHelper.capitalizeFirstName(name) must equalTo("Jean-René")
    }

    "capitalize space composed name" >> {
      val name = "JeAn ChArLeS"
      StringHelper.capitalizeFirstName(name) must equalTo("Jean-Charles")
    }

    "capitalize dash and space composed name" >> {
      val name = "JeAn ChArLeS-ReNé"
      StringHelper.capitalizeFirstName(name) must equalTo("Jean-Charles-René")
    }
  }

}
