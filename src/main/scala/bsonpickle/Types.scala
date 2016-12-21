package bsonpickle

import scala.{PartialFunction => PF}
import language.experimental.macros
import scala.annotation.implicitNotFound
import language.higherKinds
import reactivemongo.bson._

class ReaderPicker[M[_]]
class WriterPicker[M[_]]

/**
* Basic functionality to be able to read and write objects. Kept as a trait so
* other internal files can use it, while also mixing it into the `bsonpickle`
* package to form the public API
*/
trait Types{ types =>
  /**
   * Classes that provides a mutable version of [[ReadWriter]], used to
   * allow serialization and deserialization of recursive data structure
   */
  object Knot {

    class RW[T](var _write: T => BSONValue, var _read: PF[BSONValue, T]) extends types.Reader[T] with types.Writer[T] {
      def read0 = _read

      def write0 = _write

      def copyFrom(rw: types.Reader[T] with types.Writer[T]) = {
        _write = rw.write
        _read = rw.read
      }
    }

    case class Reader[T](reader0: () => types.Reader[T]) extends types.Reader[T] {
      lazy val reader = reader0()
      def read0 = reader.read0
    }

    case class Writer[T](writer0: () => types.Writer[T]) extends types.Writer[T] {
      lazy val writer = writer0()
      def write0 = writer.write0
    }
  }


  /**
   * Helper object that makes it convenient to create instances of both
   * [[Reader]] and [[Writer]] at the same time.
   */
  object ReadWriter {
    def apply[T](_write: T => BSONValue, _read: PF[BSONValue, T])
                (implicit src: sourcecode.Enclosing)
                : Writer[T] with Reader[T] = new Writer[T] with Reader[T]{
      val read0 = _read
      val write0 = _write
      override def toString = src.value
    }
  }

  type ReadWriter[T] = Reader[T] with Writer[T]

  def readerWriterVia[A, B](r: B => A, w: A => B)(implicit rB: Reader[B], wB: Writer[B]): Reader[A] with Writer[A] =
    ReadWriter(
      { case a => write(w(a))(wB) }, //
      { case bson => r(read[B](bson)(rB)) }
    )

  /**
   * A typeclass that allows you to serialize a type `T` to BSON, and
   * eventually to a string
   */
  @implicitNotFound(
    "BsonPickle does not know how to write [${T}]s; define an implicit Writer[${T}] to teach it how"
  )
  trait Writer[T]{
    def write0: T => BSONValue
    final val write: T => BSONValue = {
      case null => BSONNull
      case t => write0(t)
    }
  }
  object Writer{

    /**
     * Helper class to make it convenient to create instances of [[Writer]]
     * from the equivalent function
     */
    def apply[T](_write: T => BSONValue)
                (implicit src: sourcecode.Enclosing): Writer[T] = new Writer[T]{
      val write0 = _write
      override def toString = src.value
    }

  }
  /**
   * A typeclass that allows you to deserialize a type `T` from BSON,
   * which can itself be read from a String
   */
  @implicitNotFound(
    "BsonPickle does not know how to read [${T}]s; define an implicit Reader[${T}] to teach it how"
  )
  trait Reader[T]{
    def read0: PF[BSONValue, T]

    private val readNull: PF[BSONValue, T] = {
      case BSONNull => null.asInstanceOf[T]
    }

    final val read : PF[BSONValue, T] = new PartialFunction[BSONValue, T] {
	      def isDefinedAt(x: BSONValue) = x == BSONNull || read0.isDefinedAt(x)

	      /**
	        * Do this `isDefinedAt` dance to make sure we throw the correct error
	        * message (that of `read0` instead of `readNull` in the case where someone
	        * calls `read.apply` on some invalid value
	        */
	      def apply(v1: BSONValue): T =
	        if (!this.isDefinedAt(v1)) read0(v1)
	        else read0.applyOrElse(v1, readNull)
	  }
  }

  object Reader{
    /**
     * Helper class to make it convenient to create instances of [[Reader]]
     * from the equivalent function
     */
    def apply[T](_read: PF[BSONValue, T])
                (implicit src: sourcecode.Enclosing): Reader[T] = new Reader[T]{
      val read0 = _read
      override def toString = src.value
    }
  }

  /**
   * Handy shorthands for Reader and Writer
   */
  object Aliases{
    type R[T] = Reader[T]
    val R = Reader

    type W[T] = Writer[T]
    val W = Writer

    type RW[T] = R[T] with W[T]
    val RW = ReadWriter
  }


  /**
   * Serialize an object of type `T` to a `BSONValue`
   */
  def write[T: Writer](expr: T): BSONValue = implicitly[Writer[T]].write(expr)
  /**
   * Deserialize a `BSONValue` object of type `T`
   */
  def read[T: Reader](expr: BSONValue): T = implicitly[Reader[T]].read(expr)
}
