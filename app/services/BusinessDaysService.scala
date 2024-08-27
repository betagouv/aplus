package services

import anorm._
import anorm.SqlParser.scalar
import helper.BusinessDaysCalculator
import java.time.{LocalDate, ZonedDateTime}
import javax.inject.{Inject, Singleton}
import play.api.db.Database

@Singleton
class BusinessDaysService @Inject() (db: Database) {

  val publicHolidays: List[LocalDate] = db.withTransaction { implicit connection =>
    SQL("SELECT holiday_date FROM public_holidays").as(scalar[LocalDate].*)
  }

  private val publicHolidaySet: Set[LocalDate] = publicHolidays.toSet

  def businessHoursBetween(startTime: ZonedDateTime, endTime: ZonedDateTime): Int =
    BusinessDaysCalculator.businessHoursBetween(startTime, endTime, publicHolidaySet)

}
