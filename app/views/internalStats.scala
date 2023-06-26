package views

import cats.syntax.all._
import java.time.LocalDate
import java.util.UUID
import models.Organisation
import modules.AppConfig
import scalatags.Text.all._

object internalStats {

  case class Filters(
      startDate: LocalDate,
      endDate: LocalDate,
      areaIds: List[UUID],
      organisationIds: List[Organisation.Id],
      creatorGroupIds: List[UUID],
      invitedGroupIds: List[UUID]
  )

  def charts(filters: Filters, config: AppConfig): Frag = {
    // https://www.metabase.com/docs/latest/questions/sharing/public-links#appearance-parameters
    val appearance = "#titled=false&bordered=false" +
      "&hide_parameters=start_date,end_date"
    val startDate = s"start_date=${filters.startDate.toString}"
    val endDate = s"end_date=${filters.endDate.toString}"
    val areas =
      if (filters.areaIds.isEmpty) ""
      else "&" + filters.areaIds.map(id => s"area_id=$id").mkString("&")
    val organisations =
      if (filters.organisationIds.isEmpty) ""
      else "&" + filters.organisationIds.map(id => s"organisation_id=${id.id}").mkString("&")
    val creatorGroups =
      if (filters.creatorGroupIds.isEmpty) ""
      else "&" + filters.creatorGroupIds.map(id => s"creator_user_group_id=$id").mkString("&")
    val invitedGroups =
      if (filters.invitedGroupIds.isEmpty) ""
      else "&" + filters.invitedGroupIds.map(id => s"invited_user_group_id=$id").mkString("&")
    val metabaseFilters =
      startDate + "&" + endDate + areas + organisations + creatorGroups + invitedGroups +
        appearance

    def metabaseIframe(url: String) =
      iframe(
        src := url + "?" + metabaseFilters,
        attr("frameborder") := "0",
        // Use attr here, otherwise width and height are put in the style attribute
        attr("width") := "100%",
        attr("height") := "500",
        attr("allowtransparency").empty,
      )

    def iframe6Cols(url: String) =
      div(
        cls := "mdl-cell mdl-cell--6-col mdl-shadow--2dp mdl-color--white typography--text-align-center",
        metabaseIframe(url)
      )

    def iframe12Cols(url: String) =
      div(
        cls := "mdl-cell mdl-cell--12-col mdl-shadow--2dp mdl-color--white typography--text-align-center",
        metabaseIframe(url)
      )

    frag(
      config.statisticsNumberOfNewApplicationsUrl.map(iframe12Cols),
      config.statisticsPercentOfApplicationsByStatusUrl.map(iframe6Cols),
      config.statisticsPercentOfRelevantApplicationsUrl.map(iframe6Cols),
      config.statisticsNumberOfApplicationsByUsefulnessUrl.map(iframe12Cols),
      config.statisticsTimeToProcessApplicationsUrl.map(iframe12Cols),
    )
  }

}
