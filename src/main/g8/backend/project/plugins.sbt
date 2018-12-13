addSbtPlugin("com.eed3si9n"      % "sbt-assembly"        % "0.14.6")
addSbtPlugin("com.geirsson"      % "sbt-scalafmt"        % "1.4.0")
addSbtPlugin("com.typesafe.sbt"  % "sbt-git"             % "0.9.3")
addSbtPlugin("com.typesafe.sbt"  % "sbt-native-packager" % "1.3.6")
addSbtPlugin("de.heikoseeberger" % "sbt-header"          % "4.1.0")
addSbtPlugin("org.wartremover"   % "sbt-wartremover"     % "2.2.1")

libraryDependencies += "org.slf4j" % "slf4j-nop" % "1.7.25" // Needed by sbt-git
