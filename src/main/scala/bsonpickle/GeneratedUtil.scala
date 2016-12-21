package bsonpickle

import scala.util.Success
import reactivemongo.bson._
import language.higherKinds

/**
  * Stuff that generated code depends on but I don't want
  * to put in the stringly typed code-generator
  */
private[bsonpickle] trait GeneratedUtil {
  type Reader[T]
  type Writer[T]
  def makeReader[T](pf: PartialFunction[BSONValue, T]): Reader[T]
  def makeWriter[T](f: T => BSONValue): Writer[T]
  def read[T: Reader](bson: BSONValue): T
  def write[T: Writer](t: T): BSONValue

  // This is needed to ensure compatibility with `derive` when expanding default values
  // See https://github.com/lihaoyi/upickle-pprint/issues/143
  def writeJs[T: Writer](t: T): BSONValue = write(t)

  protected[this] def validate[T](name: String)(pf: PartialFunction[BSONValue, T]): PartialFunction[BSONValue, T]

  protected[this] def readerCaseFunction[T](names: Array[String],
      defaults: Array[BSONValue],
      read: PartialFunction[BSONValue, T]
  ): PartialFunction[BSONValue, T] = {
    validate("Object") { case x: BSONDocument => read(mapToArray(x, names, defaults)) }
  }
  protected[this] def arrayToMap(a: BSONArray, names: Array[String], defaults: Array[BSONValue]): BSONDocument = {

    val accumulated = new Array[(String, BSONValue)](names.length)
    var i = 0
    val l = a.stream.size
    while (i < l) {
      a.get(i).foreach { value =>
        if (defaults(i) != value) {
          accumulated(i) = names(i) -> value
        }
      }
      i += 1
    }

    BSONDocument(accumulated.filter(_ != null))

  }

  protected[this] def mapToArray(o: BSONDocument, names: Array[String], defaults: Array[BSONValue]) = {
    val accumulated = new Array[BSONValue](names.length)
    val map = o.stream.flatMap(_.toOption).map { case BSONElement(n, v) => (n, v) }.toMap
    var i = 0
    val l = names.length
    while (i < l) {
      if (map.contains(names(i))) accumulated(i) = map(names(i))
      else if (defaults(i) != null) accumulated(i) = defaults(i)
      else throw new Invalid.Data(o, "Key Missing: " + names(i))
      i += 1
    }
    BSONArray(accumulated)
  }
  protected[this] def RCase[T](names: Array[String],
      defaults: Array[BSONValue],
      read: PartialFunction[BSONValue, T]
  ) = {
    makeReader[T](readerCaseFunction(names, defaults, read))
  }

  protected[this] def WCase[T](names: Array[String],
      defaults: Array[BSONValue],
      write: T => BSONValue
  ) = {
    makeWriter[T](x => arrayToMap(write(x).asInstanceOf[BSONArray], names, defaults))
  }

  object BSONArraySuccess {
    def unapplySeq(arr: BSONArray): Option[Seq[BSONValue]] = Some(arr.stream.flatMap(_.toOption))
  }
}
