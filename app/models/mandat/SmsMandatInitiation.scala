package models.mandat

case class SmsMandatInitiation(
    usagerPrenom: String,
    usagerNom: String,
    usagerBirthDate: String,
    usagerPhoneLocal: String,
    hasSecuriteSociale: Boolean
)
