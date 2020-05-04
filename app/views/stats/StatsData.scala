package views.stats

import models.{Application, Area}

object StatsData {

  case class AreaAggregates(area: Area, aggregates: ApplicationAggregates)

  case class ApplicationAggregates(
      applications: List[Application],
      closedApplicationsPerMonth: List[(String, Seq[Application])],
      newApplicationsPerMonth: List[(String, Seq[Application])]
  ) {
    def count: Int = applications.size
    def countLast30Days: Int = applications.count(_.ageInDays <= 30)
  }

}

/** This class (and its subclasses) should have all "rendering" methods,
  * such that the template do not have calculations in it.
  */
case class StatsData(
    all: StatsData.ApplicationAggregates,
    aggregatesByArea: List[StatsData.AreaAggregates],
    allApplications: List[Application],
    applicationsGroupByMonths: List[(String, Seq[Application])],
    applicationsGroupByMonthsClosed: List[(String, Seq[Application])]
)
