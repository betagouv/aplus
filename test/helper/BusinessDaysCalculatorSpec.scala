package helper

import BusinessDaysCalculator.businessHoursBetween
import java.time.{ZoneId, ZonedDateTime}
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class BusinessDaysCalculatorSpec extends Specification {

  val tz = ZoneId.of("Europe/Paris")

  // 2023-09-21 = Thursday
  val date1 = ZonedDateTime.of(2023, 9, 21, 10, 0, 0, 0, tz)
  val date2 = ZonedDateTime.of(2023, 9, 21, 10, 30, 0, 0, tz)
  val date3 = ZonedDateTime.of(2023, 9, 21, 15, 0, 0, 0, tz)
  val date4 = ZonedDateTime.of(2023, 9, 21, 18, 0, 0, 0, tz)

  val date5 = ZonedDateTime.of(2023, 9, 22, 12, 0, 0, 0, tz)

  val date6 = ZonedDateTime.of(2023, 9, 23, 11, 0, 0, 0, tz)
  val date7 = ZonedDateTime.of(2023, 9, 23, 16, 0, 0, 0, tz)

  // 2023-09-24 = Sunday
  val date8 = ZonedDateTime.of(2023, 9, 24, 12, 0, 0, 0, tz)

  // 2023-09-26 = Tuesday
  val date9 = ZonedDateTime.of(2023, 9, 26, 9, 0, 0, 0, tz)
  val date10 = ZonedDateTime.of(2023, 9, 26, 16, 0, 0, 0, tz)

  "businessHoursBetween should" >> {
    "calculate hours between same day times" >> {
      businessHoursBetween(date1, date1) must equalTo(0)
      businessHoursBetween(date1, date2) must equalTo(0)
      businessHoursBetween(date1, date3) must equalTo(5)
      businessHoursBetween(date2, date3) must equalTo(5)
      businessHoursBetween(date1, date4) must equalTo(8)
    }

    "calculate hours between same week weekdays" >> {
      businessHoursBetween(date1, date5) must equalTo(26)
      businessHoursBetween(date2, date5) must equalTo(26)
      businessHoursBetween(date4, date5) must equalTo(18)
    }

    "avoid adding hours from weekend days" >> {
      businessHoursBetween(date1, date6) must equalTo(38)
      businessHoursBetween(date2, date6) must equalTo(38)
      businessHoursBetween(date1, date7) must equalTo(38)
      businessHoursBetween(date6, date7) must equalTo(0)
      businessHoursBetween(date6, date8) must equalTo(0)
    }

    "calculate hours between different week weekdays" >> {
      businessHoursBetween(date1, date9) must equalTo(71)
      businessHoursBetween(date2, date9) must equalTo(71)
      businessHoursBetween(date1, date10) must equalTo(78)
      businessHoursBetween(date2, date10) must equalTo(78)
    }

    "be symetrical when start and end are reversed" >> {
      businessHoursBetween(date2, date1) must equalTo(0)
      businessHoursBetween(date3, date1) must equalTo(5)
      businessHoursBetween(date3, date2) must equalTo(5)
      businessHoursBetween(date5, date1) must equalTo(26)
      businessHoursBetween(date8, date6) must equalTo(0)
      businessHoursBetween(date9, date1) must equalTo(71)
      businessHoursBetween(date9, date2) must equalTo(71)
    }
  }

}
