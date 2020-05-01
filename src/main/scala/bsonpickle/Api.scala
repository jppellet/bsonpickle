package bsonpickle

import language.experimental.macros
import scala.reflect.ClassTag
import language.higherKinds
import reactivemongo.bson._
import scala.util.Success

/**
  * An instance of the bsonpickle API. There's a default instance at
  * `bsonpickle.default`, but you can also implement it yourself to customize
  * its behavior. Override the `annotate` methods to control how a sealed
  * trait instance is tagged during reading and writing.
  */
trait Api extends Types with Implicits with Generated with LowPriX with BsonPrint {
  protected[this] def validate[T](name: String)(pf: PartialFunction[BSONValue, T]) = Internal.validate(name)(pf)

  type key = derive.key

  def annotate[V: ClassTag](rw: Reader[V], n: String): Reader[V]
  def annotate[V: ClassTag](rw: Writer[V], n: String): Writer[V]
}

/**
  * The default way of accessing bsonpickle
  */
object default extends AttributeTagged


/**
  * A `bsonppickle.Api` that follows the default sealed-trait-instance-tagging
  * behavior of using an attribute, but allow you to control what the name
  * of the attribute is.
  */
trait AttributeTagged extends Api {
  def tagName = "_type"
  def annotate[V: ClassTag](rw: Reader[V], n: String) = Reader[V] {
    case doc: BSONDocument if doc.get(tagName).contains(BSONString(n)) =>
      rw.read(BSONDocument(doc.stream.filter {
        case Success(BSONElement(someTagName, _)) if someTagName == tagName => false
        case _ => true
      }))

  }

  def annotate[V: ClassTag](rw: Writer[V], n: String) = Writer[V] { case x: V =>
    BSONDocument(Success(BSONElement(tagName, BSONString(n))) #:: rw.write(x).asInstanceOf[BSONDocument].stream)
  }
}

/**
  * A `bsonppickle.Api` that works like `AttributeTagged`, but uses a simple
  * string representation when there are no other fields to serialize rather
  * than a `BSONDocument` with only one field named `tagName`.
  */
trait CompactAttributeTagged extends AttributeTagged {

  override def annotate[V: ClassTag](rw: Reader[V], n: String) = Reader[V] {
    case BSONString(`n`) =>
      rw.read(BSONDocument.empty)
    case doc: BSONDocument if doc.get(tagName).contains(BSONString(n)) =>
      rw.read(BSONDocument(doc.stream.filter {
        case Success(BSONElement(someTagName, _)) if someTagName == tagName => false
        case _ => true
      }))
  }

  override def annotate[V: ClassTag](rw: Writer[V], n: String) = Writer[V] {
    case x: V =>
      val otherMembers = rw.write(x).asInstanceOf[BSONDocument].stream
      if (otherMembers.isEmpty)
        BSONString(n)
      else
        BSONDocument(Success(BSONElement(tagName, BSONString(n))) #:: otherMembers)
  }

}


/**
  * Stupid hacks to work around scalac not forwarding macro type params properly
  */
object Forwarder {
  def dieIfNothing[T: c.WeakTypeTag]
  (c: derive.ScalaVersionStubs.Context)
      (name: String) = {
    if (c.weakTypeOf[T] =:= c.weakTypeOf[Nothing]) {
      c.abort(
        c.enclosingPosition,
        s"BsonPickle is trying to infer a $name[Nothing]. That probably means you messed up"
      )
    }
  }
  def applyR[T](c: derive.ScalaVersionStubs.Context)
      (implicit e: c.WeakTypeTag[T]): c.Expr[T] = {
    import c.universe._
    dieIfNothing[T](c)("Reader")
    c.Expr[T](q"${ c.prefix }.macroR0[$e, ${ c.prefix }.Reader]")
  }
  def applyW[T](c: derive.ScalaVersionStubs.Context)
      (implicit e: c.WeakTypeTag[T]): c.Expr[T] = {
    import c.universe._
    dieIfNothing[T](c)("Writer")
    c.Expr[T](q"${ c.prefix }.macroW0[$e, ${ c.prefix }.Writer]")
  }
  def applyRW[T](c: derive.ScalaVersionStubs.Context)
                (implicit e: c.WeakTypeTag[T]): c.Expr[T] = {
    import c.universe._
    dieIfNothing[T](c)("ReadWriter")
    c.Expr[T](q"${c.prefix}.macroRW0[$e, ${c.prefix}.Reader, ${c.prefix}.Writer]")
  }
}
trait LowPriX {
  this: Api =>
  implicit def macroR[T]: Reader[T] = macro Forwarder.applyR[T]
  implicit def macroW[T]: Writer[T] = macro Forwarder.applyW[T]
  def macroRW[T]: ReadWriter[T] = macro Forwarder.applyRW[T]
  def macroR0[T, M[_]]: Reader[T] = macro Macros.macroRImpl[T, M]
  def macroW0[T, M[_]]: Writer[T] = macro Macros.macroWImpl[T, M]
  def macroRW0[T, RM[_], WM[_]]: ReadWriter[T] = macro Macros.macroRWImpl[T, RM, WM]
}

trait BsonPrint {

  import reactivemongo.bson._
  import scala.util.{ Try, Success, Failure }

  private def indentation(indent: Int) = "  " * indent

  def bsonToString(value: BSONValue, indent: Int = 0, withOneLiners: Boolean = false, arrayLimit: Option[Int] = None, alignObjectValues: Boolean = false): String = {
    value match {
      case doc: BSONDocument =>
        val elems: Seq[(String, String)] = doc.stream.map(e => bsonFieldToString(e, indent + 1, withOneLiners, arrayLimit, alignObjectValues))
        if (elems.isEmpty) "{}"
        else {
          if (withOneLiners && elems.size == 1) s"{${ (elems.head._1 + elems.head._2).trim }}"
          else {
            val lines =
              if (!alignObjectValues) elems.map { case (n, v) => n + v }
              else {
                val maxFieldWidth = elems.map(_._1.size).max
                elems.map { case (n, v) => n.padTo(maxFieldWidth, ' ') + v }
              }
            s"{\n${ lines.mkString(",\n") }\n${ indentation(indent) }}"
          }
        }
      case arr: BSONArray =>
        val arrayElemsAsSeq = arr.stream.toSeq
        val (limit, source) = arrayLimit.map(limit => limit -> arrayElemsAsSeq.take(limit + 1)).getOrElse(-1 -> arrayElemsAsSeq)
        val elems: Seq[String] = source.zipWithIndex.map { case (e, i) =>
          if (i == limit) s"(${ arrayElemsAsSeq.size - limit } more)"
          else bsonValueTryToString(e, indent, withOneLiners, arrayLimit, alignObjectValues)
        }
        if (elems.isEmpty) "[]"
        else elems.mkString("[", ", ", "]")
      case id: BSONObjectID => id.stringify
      case BSONString(value) => "\"" + value + "\""
      case BSONInteger(value) => s"$value"
      case BSONLong(value) => s"${ value }L"
      case BSONDouble(value) => s"$value"
      case BSONBoolean(value) => value.toString
      case BSONDateTime(value) => java.time.Instant.ofEpochMilli(value).toString
      case other => other.toString
    }
  }

  def bsonFieldToString(valueTry: Try[BSONElement], indent: Int = 0, withOneLiners: Boolean = false, arrayLimit: Option[Int] = None, alignObjectValues: Boolean = false): (String, String) = {
    valueTry match {
      case Failure(e) =>
        ("", s"${ indentation(indent) }ERROR[${ e.getMessage }]")
      case Success(BSONElement(name, value)) =>
        val indentedName = s"${ indentation(indent) }${ name }: "
        val valueStr = bsonToString(value, indent, withOneLiners, arrayLimit, alignObjectValues)
        (indentedName, valueStr)
    }
  }

  def bsonValueTryToString(valueTry: Try[BSONValue], indent: Int = 0, withOneLiners: Boolean = false, arrayLimit: Option[Int] = None, alignObjectValues: Boolean = false): String = {
    valueTry match {
      case Failure(e) =>
        val indentation = "  " * indent
        s"${ indentation }ERROR[${ e.getMessage }]"
      case Success(value) =>
        bsonToString(value, indent, withOneLiners, arrayLimit, alignObjectValues)
    }
  }

  implicit class BSONValueExt[B <: BSONValue](private val value: B) {
    def show: B = {
      println(bsonToString(value));
      value
    }
  }

}
