# bsonpickle

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/name.pellet.jp/bsonpickle_2.11/badge.svg?style=flat)](http://mvnrepository.com/artifact/name.pellet.jp/bsonpickle_2.11) [![Join the chat at https://gitter.im/jppellet/bsonpickle](https://badges.gitter.im/jppellet/bsonpickle.svg)](https://gitter.im/jppellet/bsonpickle?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

**bsonpickle** is a modified version of [µPickle](http://www.lihaoyi.com/upickle-pprint/upickle/) which implicitly derives `Reader` and `Writer` instances for common Scala data structures (`Option`, `Either`, most collection classes), case classes and sealed trait hierarchies to serialize data to BSON for use with the [ReactiveMongo](http://reactivemongo.org) driver.

For more details on how you use or customize bsonpickle, please refer to the [µPickle] documentation.

Big thanks to [Li Haoyi](https://github.com/lihaoyi) for creating µPickle!

## Getting Started

Add this to `build.sbt`:

`libraryDependencies += "name.pellet.jp" %% "bsonpickle" % <latest published version>`
	
Release 0.4.4.1 is based on µPickle 0.4.4 and on the BSON classes found in ReactiveMongo 0.12.

## Why serialize to BSON directly?

Most probably, JSON serializers are already available for your data, and the [Play plugin for ReactiveMongo](https://github.com/ReactiveMongo/Play-ReactiveMongo) allows you to use Play's JSON classes directly to interact with Mongo and MongoDB collections. While it is perfectly possible to do so, the data inserted into MongoDB that way is not always ideally represented:

 * JSON makes no difference between `Int`, `Long`, `Double`. For some values, your JSON representation may even store them as strings. BSON stores each type in a specific type-aware container.
 * Additionally, BSON has dedicated ways to represent these types, which JSON doesn't:
   - dates
   - binary data

Moreover, representing all data with the appropriate BSON wrapper…
 * enables a more compact representation in MongoDB
 * enables fast type-aware queries like numerical comparisons
 * enables type-aware sorting
 * preserves the type information for other MongoDB drivers
 * makes it show up correctly in GUI tools (such as [Robomongo](https://robomongo.org)).

## Type Mappings

Here's how native Scala types are mapped to BSON classes from the ReactiveMongo driver out of the box. Additional mappings can of course be defined.

| Scala Type | BSON Type |
| --- | --- |
| `Boolean` | `BSONBoolean` |
| `String` | `BSONString` |
| `Symbol` | `BSONString` |
| `Char` | `BSONString` of length 1 |
| `Int` | `BSONInteger` |
| `Short` | `BSONInteger` |
| `Byte` | `BSONInteger` |
| `Long` | `BSONLong` |
| `Double` | `BSONDouble` |
| `Float` | `BSONDouble` |
| `java.util.Date` | `BSONDateTime` |
| `java.time.Instant` | `BSONDateTime` |
| `scala.concurrent.duration.Duration` | `BSONLong` storing nanoseconds, or `BSONString` for the special values `"inf"`, `"-inf"` and `"undef"`
| `java.util.UUID` | `BSONString` |
| `Array[Byte]` | `BSONBinary` |
| `Tuple1` to `Tuple22` | `BSONArray` with a fixed size |
| Collection<sup>1, 2</sup> | `BSONArray` |
| `Map` | `BSONDocument` if keys are of type `String`, else `BSONArray` of `BSONArray`s with size 2
| `Option` | `BSONArray` with 0 or 1 elements |
| `Either` | `BSONArray` with 2 elements, the first of which it a `BSONBoolean` indicating `Left` or `Right`
| Unit, other singletons | empty `BSONDocument` |
| Case classes<sup>1, 3</sup> | `BSONDocument` |
| Sealed trait hiearchies<sup>1, 4</sup> | `BSONDocument` |

**Notes**
 1. Provided the values it contains can themselves be represented as BSON.
 2. “Collection” here means any generic type `V` containing elements of type `T` for which a `CanBuildFrom[Nothing, T, V[T]]` is implicitly available.
 3. If you’ve defined custom field names with the `derive.key` annotation, they will be picked up by both µPickle and bsonpickle.
 4. Concrete type information is stored in a field named `"_type"` (and not `"$type"` as µPickle does, as MongoDB disallows the use of the dollar character in field names). This is customizable.

### Versioning Scheme

bsonpickle is heavily based on µPickle's code. Therefore, its version number reflects the version number of µPickle is it based on. The last segment indicates iterations within bsonpickle. For instance, bsonpickle version 0.3.9.1 is iteration 1 of the library based on µPickle 0.3.9. As both µPickle and bsonpickle are closely tied to the Li Haoyi's `derive` library, you may run into problems if you use them both with different reference versions.

[µPickle]: http://www.lihaoyi.com/upickle-pprint/upickle/
