package models

import org.joda.time.DateTime

case class Application(id: String,
                       status: String,
                       creationDate: DateTime,
                       author: String,
                       subject: String,
                       description: String)