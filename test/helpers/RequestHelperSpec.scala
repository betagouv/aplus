package csv

import org.junit.runner.RunWith
import org.specs2.mutable.{Specification, Tables}
import org.specs2.runner.JUnitRunner
import helper.RequestHelper

@RunWith(classOf[JUnitRunner])
class RequestHelperSpec extends Specification with Tables {

  "The User-Agent parser" should {
    "recognize Internet Explorer" in {
      // http://useragentstring.com/pages/useragentstring.php?name=Internet+Explorer
      "User-Agent" | "Is IE" |>
        // Internet Explorer
        "Mozilla/5.0 (compatible; MSIE 6.0; Windows NT 5.1)" ! true | // IE 6
        "Mozilla/5.0 (Windows; U; MSIE 7.0; Windows NT 6.0; en-US)" ! true | // IE 7
        "Mozilla/5.0 (compatible; MSIE 8.0; Windows NT 6.1; Trident/4.0; GTB7.4; InfoPath.2; SV1; .NET CLR 3.3.69573; WOW64; en-US)" ! true | // IE 8
        "Mozilla/5.0 (Windows; U; MSIE 9.0; Windows NT 9.0; en-US)" ! true | // IE 9
        "Mozilla/5.0 (compatible; MSIE 9.0; Windows NT 7.1; Trident/5.0)" ! true | // IE 9
        "Mozilla/5.0 (compatible; MSIE 10.0; Windows NT 6.2; Trident/6.0)" ! true | // IE 10
        "Mozilla/5.0 (Windows NT 6.3; Trident/7.0; rv:11.0) like Gecko" ! true | // IE 11
        // Edge
        "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/39.0.2171.71 Safari/537.36 Edge/12.0" ! false | // Edge 12
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/46.0.2486.0 Safari/537.36 Edge/13.10586" ! false | // Edge 13
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/46.0.2486.0 Safari/537.36 Edge/14.14300" ! false | // Edge 14
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/64.0.3282.140 Safari/537.36 Edge/17.17134" ! false | // Edge 17
        // Chrome
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.77 Safari/537.36" ! false | // Chrome
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2227.1 Safari/537.36" ! false | // Chrome
        // Firefox
        "Mozilla/5.0 (X11; Linux i686; rv:64.0) Gecko/20100101 Firefox/64.0" ! false | // Firefox
        "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:64.0) Gecko/20100101 Firefox/64.0" ! false | // Firefox
        { (userAgent: String, isIE: Boolean) =>
          RequestHelper.userAgentIsInternetExplorer(userAgent) mustEqual isIE
        }
    }
  }

}
