package views

import cats.syntax.all._
import helper.Time
import java.time.LocalDate
import java.util.UUID
import models.{Area, Organisation, UserGroup}
import modules.AppConfig
import scalatags.Text.all._

object internalStats {

  case class Filters(
      startDate: LocalDate,
      endDate: LocalDate,
      areaIds: List[UUID],
      organisationIds: List[Organisation.Id],
      creatorGroupIds: List[UUID],
      invitedGroupIds: List[UUID],
      isInFranceServicesNetwork: Option[Boolean]
  )

  case class SelectionForm(
      canFilterByOrganisation: Boolean,
      areasThatCanBeFilteredBy: List[Area],
      organisationsThatCanBeFilteredBy: List[Organisation],
      groupsThatCanBeFilteredBy: List[UserGroup],
      creationMinDate: LocalDate,
      creationMaxDate: LocalDate,
      selectedAreaIds: List[UUID],
      selectedOrganisationIds: List[Organisation.Id],
      selectedGroupIds: List[UUID],
      hasNetworkFilters: Boolean,
      isInFranceServicesNetwork: Option[Boolean],
  )

  def charts(filters: Filters, config: AppConfig): Frag = {
    // https://www.metabase.com/docs/latest/questions/sharing/public-links#appearance-parameters
    val appearance = "#bordered=false" +
      "&hide_parameters=start_date,end_date,area_id,organisation_id,creator_user_group_id,invited_user_group_id,is_in_france_services_network"
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
    val isInFranceServicesNetwork =
      filters.isInFranceServicesNetwork match {
        case None        => ""
        case Some(true)  => "&is_in_france_services_network=true"
        case Some(false) => "&is_in_france_services_network=false"
      }
    val metabaseFilters =
      startDate + "&" + endDate + areas + organisations + creatorGroups + invitedGroups +
        isInFranceServicesNetwork + appearance

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
        cls := "aplus-half-col mdl-cell mdl-cell--6-col mdl-shadow--2dp mdl-color--white typography--text-align-center",
        metabaseIframe(url)
      )

    def iframe12Cols(url: String) =
      div(
        cls := "aplus-half-col mdl-cell mdl-cell--12-col mdl-shadow--2dp mdl-color--white typography--text-align-center",
        metabaseIframe(url)
      )

    frag(
      config.statisticsNumberOfNewApplicationsUrl.map(iframe12Cols),
      config.statisticsPercentOfApplicationsByStatusUrl.map(iframe6Cols),
      config.statisticsPercentOfRelevantApplicationsUrl.map(iframe6Cols),
      frag(config.statisticsBottomChartsUrls.map(iframe12Cols)),
      (filters.endDate
        .isAfter(LocalDate.now().minusDays(2)))
        .some
        .filter(identity)
        .map(_ =>
          p(
            "Attention, certaines demandes du ",
            Time.formatPatternFr(LocalDate.now().minusDays(1), "dd/MM/YY"),
            " et du ",
            Time.formatPatternFr(LocalDate.now(), "dd/MM/YY"),
            " peuvent ne pas être comptabilisées."
          )
        )
    )
  }

}
