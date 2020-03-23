package views.stats

import models.{Application, Area}

object StatsData {

  case class AreaAggregates(
      area: Area,
      applications: List[Application],
      closedApplicationsPerMonth: List[(String, Seq[Application])],
      newApplicationsPerMonth: List[(String, Seq[Application])]
  )

}

case class StatsData(
    allApplications: List[Application],
    areaAggregates: List[StatsData.AreaAggregates],
    applicationsGroupByMonths: List[(String, Seq[Application])],
    applicationsGroupByMonthsClosed: List[(String, Seq[Application])]
)
