package bsonpickle

private[bsonpickle] trait BigDecimalSupport {

  @inline protected def exactBigDecimal(s: String): BigDecimal = BigDecimal(s)
}
