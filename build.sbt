// Versions

val referenceUpickleAndDeriveVersion = "0.3.9"
val reactiveMongoVersion = "0.11.10"
val bsonPickleMinor = "0-SNAPSHOT"

val bsonPickleVersion = s"$referenceUpickleAndDeriveVersion.$bsonPickleMinor"


val bsonpickle = (project in file("."))
  .settings(
  	organization := "name.pellet",
    name := "bsonpickle",
	version := bsonPickleVersion,
	scalaVersion := "2.11.7",
    
	scalacOptions := Seq(
	  "-unchecked",
	  "-deprecation",
	  "-encoding", "utf8",
	  "-feature"
	),
//	testFrameworks += new TestFramework("utest.runner.Framework"),
	libraryDependencies ++= Seq(
//	  "com.lihaoyi" %% "utest" % "0.3.1" % "test",
	  "org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided",
	  "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided",
	  "com.lihaoyi" %% "derive" % referenceUpickleAndDeriveVersion,
	  "org.reactivemongo" %% "reactivemongo" % reactiveMongoVersion
	) ++ (
	  if (scalaVersion.value startsWith "2.11.") Nil
	  else Seq(
	    "org.scalamacros" %% s"quasiquotes" % "2.0.0" % "provided",
	    compilerPlugin("org.scalamacros" % s"paradise" % "2.1.0-M5" cross CrossVersion.full)
	  )
	),
	unmanagedSourceDirectories in Compile ++= {
	  if (scalaVersion.value startsWith "2.10.") Seq(baseDirectory.value / ".."/"shared"/"src"/ "main" / "scala-2.10")
	  else Seq(baseDirectory.value / ".."/"shared" / "src"/"main" / "scala-2.11")
	},
    sourceGenerators in Compile <+= sourceManaged in Compile map { dir =>
      val file = dir / "bsonpickle" / "Generated.scala"
      val tuplesAndCases = (1 to 22).map{ i =>
        def commaSeparated(s: Int => String) = (1 to i).map(s).mkString(", ")
        val writerTypes = commaSeparated(j => s"T$j: Writer")
        val readerTypes = commaSeparated(j => s"T$j: Reader")
        val typeTuple = commaSeparated(j => s"T$j")
        val written = commaSeparated(j => s"writeBson(x._$j)")
        val pattern = commaSeparated(j => s"x$j")
        val read = commaSeparated(j => s"readBson[T$j](x$j)")
        val caseReader =
          if(i == 1) s"f(readBson[Tuple1[T1]](x)._1)"
          else s"f.tupled(readBson[Tuple$i[$typeTuple]](x))"
        (s"""
          implicit def Tuple${i}W[$writerTypes] = makeWriter[Tuple${i}[$typeTuple]](
            x => BSONArray($written)
          )
          implicit def Tuple${i}R[$readerTypes] = makeReader[Tuple${i}[$typeTuple]](
            validate("Array(${i})"){case BSONArraySuccess($pattern) => Tuple${i}($read)}
          )
          """, s"""
          def Case${i}R[$readerTypes, V]
                       (f: ($typeTuple) => V, names: Array[String], defaults: Array[BSONValue])
            = RCase[V](names, defaults, {case x => $caseReader})
          def Case${i}W[$writerTypes, V]
                       (g: V => Option[Tuple${i}[$typeTuple]], names: Array[String], defaults: Array[BSONValue])
            = WCase[V](names, defaults, x => writeBson(g(x).get))
          """)
      }
      val (tuples, cases) = tuplesAndCases.unzip
      IO.write(file, s"""
          package bsonpickle
          import language.experimental.macros
          import reactivemongo.bson._
          /**
           * Auto-generated picklers and unpicklers, used for creating the 22
           * versions of tuple-picklers and case-class picklers
           */
          trait Generated extends GeneratedUtil{
            ${tuples.mkString("\n")}
          }
        """)
      Seq(file)
    },

	initialCommands in console := """
	  import bsonpickle.default._
	  import reactivemongo.bson._
	""",
	triggeredMessage := Watched.clearWhenTriggered
  )

onLoad in Global := (Command.process("project bsonpickle", _: State)) compose (onLoad in Global).value
