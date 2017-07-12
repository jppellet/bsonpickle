// Versions

val reactiveMongoVersion = "0.12.5"
val referenceUpickleAndDeriveVersion = "0.4.4"
val myScalaVersion = "2.12.2"
val bsonPickleMinor = "2"

val bsonPickleVersion = s"$referenceUpickleAndDeriveVersion.$bsonPickleMinor"

val compatible211and212 = 
  unmanagedSourceDirectories in Compile ++= {
    if (Set("2.11", "2.12").contains(scalaBinaryVersion.value)) 
      Seq(sourceDirectory.value / "main" / "scala-2.11_2.12")
    else
      Seq()
  }

val bsonpickle = (project in file("."))
  .settings(
  	organization := "name.pellet.jp",
    name := "bsonpickle",
	version := bsonPickleVersion,
	scalaVersion := myScalaVersion,
    
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
	  if(scalaBinaryVersion.value == "2.10")
	    Seq(
	      compilerPlugin("org.scalamacros" % s"paradise" % "2.1.0" cross CrossVersion.full),
	      "org.scalamacros" %% s"quasiquotes" % "2.1.0"
	    )
	  else Seq()
	),
	compatible211and212,
    sourceGenerators in Compile += Def.task{
      val dir = (sourceManaged in Compile).value
      val file = dir / "bsonpickle" / "Generated.scala"
      val tuplesAndCases = (1 to 22).map{ i =>
        def commaSeparated(s: Int => String) = (1 to i).map(s).mkString(", ")
        val writerTypes = commaSeparated(j => s"T$j: Writer")
        val readerTypes = commaSeparated(j => s"T$j: Reader")
        val typeTuple = commaSeparated(j => s"T$j")
        val written = commaSeparated(j => s"write(x._$j)")
        val pattern = commaSeparated(j => s"x$j")
        val read = commaSeparated(j => s"read[T$j](x$j)")
        val caseReader =
          if(i == 1) s"f(read[Tuple1[T1]](x)._1)"
          else s"f.tupled(read[Tuple$i[$typeTuple]](x))"
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
            = WCase[V](names, defaults, x => write(g(x).get))
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
    }.taskValue,

	// console
	initialCommands in console := """
	  import bsonpickle.default._
	  import reactivemongo.bson._
	""",
	triggeredMessage := Watched.clearWhenTriggered,
	
	// Sonatype
	publishMavenStyle := true,
    publishTo := {
	  val sonatype = "https://oss.sonatype.org/"
	  if (isSnapshot.value)
	    Some("snapshots" at sonatype + "content/repositories/snapshots")
	  else
	    Some("releases"  at sonatype + "service/local/staging/deploy/maven2")
	},
	publishArtifact in Test := false,
	pomIncludeRepository := { _ => false },
	pomExtra := (
	  <url>https://github.com/jppellet/bsonpickle</url>
	  <licenses>
	    <license>
          <name>MIT license</name>
          <url>http://www.opensource.org/licenses/mit-license.php</url>
	    </license>
	  </licenses>
	  <scm>
	    <url>git@github.com:jppellet/bsonpickle.git</url>
	    <connection>scm:git:git@github.com:jppellet/bsonpickle.git</connection>
	  </scm>
	  <developers>
	    <developer>
	      <id>jppellet</id>
	      <name>Jean-Philippe Pellet</name>
	      <url>http://jp.pellet.name</url>
	    </developer>
	  </developers>
	)
	
  )

onLoad in Global := (Command.process("project bsonpickle", _: State)) compose (onLoad in Global).value
