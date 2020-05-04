package views.stats

import helper.Time
import java.time.ZonedDateTime
import scala.collection.immutable.ListMap
import models.{Application, Area}

object StatsData {

  case class AreaAggregates(area: Area, aggregates: ApplicationAggregates)

  case class ApplicationAggregates(
      applications: List[Application],
      months: ListMap[String, String]
  ) {
    def count: Int = applications.size
    lazy val countLast30Days: Int = applications.count(_.ageInDays <= 30)
    lazy val countClosedLast30Days: Int = applications.count(a => a.ageInDays <= 30 && a.closed)
    lazy val countRelevant: Int = applications.count(!_.irrelevant)
    lazy val countIrrelevant: Int = applications.count(_.irrelevant)

    lazy val countIrrelevantLast30Days: Int =
      applications.count(a => a.ageInDays <= 30 && a.irrelevant)

    lazy val applicationsByStatus: Map[String, List[Application]] = applications.groupBy(_.status)

    lazy val applicationsGroupedByMonth: List[(String, List[Application])] =
      months.values.toList.map { month: String =>
        month -> applications
          .filter(application =>
            (Time
              .formatPatternFr(application.creationDate, "MMMM YYYY"): String) == (month: String)
          )
          .toList
      }

    lazy val closedApplicationsGroupedByMonth: List[(String, List[Application])] =
      months.values.toList.map { month: String =>
        month -> applications
          .filter(application =>
            application.estimatedClosedDate
              .map(closedDate =>
                (Time.formatPatternFr(closedDate, "MMMM YYYY"): String) == (month: String)
              )
              .getOrElse(false)
          )
          .toList
      }
  }

}

/** This class (and its subclasses) should have all "rendering" methods,
  * such that the template do not have calculations in it.
  */
case class StatsData(
    all: StatsData.ApplicationAggregates,
    aggregatesByArea: List[StatsData.AreaAggregates]
)
