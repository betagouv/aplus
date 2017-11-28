package models

import java.util.UUID

case class User(id: UUID,
                key: String,
                name: String,
                qualite: String,
                email: String,
                helper: Boolean,
                instructor: Boolean,
                admin: Boolean,
                areas: List[UUID]) {
  def nameWithQualite = s"$name ( $qualite )"
}