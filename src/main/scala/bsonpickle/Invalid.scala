package bsonpickle

import scala.reflect.ClassTag
import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.language.higherKinds
import scala.language.experimental.macros
import java.util.UUID

import reactivemongo.bson._


sealed trait Invalid extends Exception
object Invalid{

  /**
    * Thrown when bsonpickle tries to convert a BSON value into a given data
    * structure but fails because part the blob is invalid
    *
    * @param data The section of the BSON value that bsonpickle tried to convert.
    *             This could be the entire blob, or it could be some subtree.
    * @param msg Human-readable text saying what went wrong
    */
  case class Data(data: BSONValue, msg: String)
      extends Exception(s"$msg (data: ${bsonpickle.default.bsonToString(data)})")
              with Invalid
}
