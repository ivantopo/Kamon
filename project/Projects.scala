/* =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

import sbt._
import Keys._

object Projects extends Build {
  import AspectJ._
  import Settings._
  import Dependencies._

  lazy val kamon = Project("kamon", file("."))
    .aggregate(kamonCore, kamonTestkit, kamonAutoweave)
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(noPublishing: _*)


  lazy val kamonCore: Project = Project("kamon-core", file("kamon-core"))
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(aspectJSettings: _*)
    .settings(
      javacOptions in Compile ++= Seq("-XDignore.symbol.file"),
      libraryDependencies ++=
        compile(akkaActor, hdrHistogram, typesafeConfig, slf4jApi) ++
        provided(aspectJ) ++
        optional(logback) ++
        test(scalatest, akkaSlf4j, slf4jJul, slf4jLog4j, logback))

  lazy val kamonTestkit = Project("kamon-testkit", file("kamon-testkit"))
    .dependsOn(kamonCore)
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(
      libraryDependencies ++=
        compile(akkaActor) ++
        provided(aspectJ) ++
        test(slf4jApi, slf4jnop))

  lazy val kamonAutoweave = Project("kamon-autoweave", file("kamon-autoweave"))
    .dependsOn(kamonCore % "compile->compile;test->test")
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(
      libraryDependencies ++=
        test(scalatest, slf4jApi) ++
        compile(aspectJ))

  val noPublishing = Seq(publish := (), publishLocal := (), publishArtifact := false)
}
