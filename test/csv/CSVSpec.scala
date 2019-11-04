package csv

import extentions.CSVUtil
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class CSVSpec extends Specification {

  "The 'ap;l\"us' string should" >> {
    "be escaped as '\"ap;l\"\"us\"'" >> {
      CSVUtil.escape("ap;l\"us") mustEqual "\"ap;l\"\"us\""
    }
  }
}
