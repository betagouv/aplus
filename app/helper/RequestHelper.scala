package helper

import play.api.http.HeaderNames
import play.api.mvc.RequestHeader

object RequestHelper {

  def userAgentIsInternetExplorer(request: RequestHeader): Boolean =
    request.headers.get(HeaderNames.USER_AGENT).map(userAgentIsInternetExplorer).getOrElse(false)

  /** Checks taken from
    * https://stackoverflow.com/questions/19999388/check-if-user-is-using-ie
    * We don't use a User-Agent parsing lib because they are generally very heavy.
    */
  def userAgentIsInternetExplorer(userAgent: String): Boolean =
    userAgent.contains("MSIE ") ||
      tridentRegex.findFirstIn(userAgent).nonEmpty

  private val tridentRegex = """Trident.*rv:11\.""".r

}
