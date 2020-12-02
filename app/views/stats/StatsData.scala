package views.stats

import cats.kernel.Eq
import cats.syntax.all._
import java.time.YearMonth
import helper.Time
import models.{Application, Area, User}

object StatsData {

  case class Label(label: String) extends AnyVal

  object Label {
    implicit val Eq: Eq[Label] = (x: Label, y: Label) => x.label === y.label
  }

  case class TimeSeries(points: List[(Label, Int)])

  case class ConditionalTimeSeries(series: List[(Label, TimeSeries)], timeAxis: List[Label]) {

    lazy val conditions: List[Label] = series.map(_._1)

    /** For HTML tables */
    lazy val transpose: List[(String, List[(String, Int)])] =
      timeAxis.map(timePoint =>
        (
          timePoint.label,
          series.map { case (condition, singleTimeSeries) =>
            (
              condition.label,
              singleTimeSeries.points
                .find(t => t._1 === timePoint)
                .map(_._2)
                // not pretty, maybe figure out how to have some Option / NaN
                .getOrElse[Int](0)
            )
          }
        )
      )

  }

  case class AreaAggregates(area: Area, aggregates: ApplicationAggregates)

  case class ApplicationAggregates(
      applications: List[Application],
      months: List[YearMonth],
      private val usersRelatedToApplications: List[User]
  ) {
    def count: Int = applications.size
    lazy val countLast30Days: Int = applications.count(_.ageInDays <= 30)
    lazy val countClosedLast30Days: Int = applications.count(a => a.ageInDays <= 30 && a.closed)
    lazy val countRelevant: Int = applications.count(!_.irrelevant)
    lazy val countIrrelevant: Int = applications.count(_.irrelevant)

    lazy val countIrrelevantLast30Days: Int =
      applications.count(a => a.ageInDays <= 30 && a.irrelevant)

    lazy val applicationsByStatus: Map[String, List[Application]] = applications.groupBy(_.status)

    lazy val applicationsGroupedByMonth: List[(String, List[Application])] = {
      val grouped = applications.groupBy(application => YearMonth.from(application.creationDate))
      months.map { month =>
        Time.formatMonthYearAllLetters(month) -> grouped.getOrElse(month, List.empty[Application])
      }
    }

    lazy val closedApplicationsGroupedByMonth: List[(String, List[Application])] = {
      val grouped =
        applications.groupBy(application => application.estimatedClosedDate.map(YearMonth.from))
      months.map { month =>
        Time.formatMonthYearAllLetters(month) -> grouped.getOrElse(
          month.some,
          List.empty[Application]
        )
      }
    }

    private def applicationsCountByMandat(mandatType: Application.MandatType): List[Int] =
      applicationsGroupedByMonth.map(
        _._2.count(application => application.mandatType === mandatType.some)
      )

    lazy val applicationsCountByMandatPaper: List[Int] =
      applicationsGroupedByMonth.map(
        _._2
          .count(application =>
            application.mandatType.isEmpty ||
              application.mandatType === Application.MandatType.Paper.some
          )
      )

    lazy val applicationsCountByMandatSms: List[Int] =
      applicationsCountByMandat(Application.MandatType.Sms)

    lazy val applicationsCountByMandatPhone: List[Int] =
      applicationsCountByMandat(Application.MandatType.Phone)

    // Conditional Series

    lazy val creatorQualitees: List[String] =
      applicationsGroupedByMonth
        .flatMap(_._2)
        .flatMap(_.creatorUserQualite(usersRelatedToApplications))
        .distinct

    lazy val applicationsCountGroupedByCreatorQualiteThenByMonth: ConditionalTimeSeries =
      ConditionalTimeSeries(
        series = creatorQualitees.map(qualite =>
          (
            Label(qualite),
            TimeSeries(
              applicationsGroupedByMonth
                .map { case (month, applications) =>
                  (
                    Label(month),
                    applications.count(
                      _.creatorUserQualite(usersRelatedToApplications).contains(qualite)
                    )
                  )
                }
            )
          )
        ),
        timeAxis = months.map(month => Label(Time.formatMonthYearAllLetters(month)))
      )

  }

}

/** This class (and its subclasses) should have all "computation" methods,
  * such that the template do not have calculations in it.
  */
case class StatsData(
    all: StatsData.ApplicationAggregates,
    aggregatesByArea: List[StatsData.AreaAggregates]
)
