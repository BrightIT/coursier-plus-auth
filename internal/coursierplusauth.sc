import $file.base64
import $file.credentials
import $ivy.`io.get-coursier::coursier-cli:1.0.0`

import coursier.cli._
import upickle.{default => upickled}
import java.nio.file._

def credCheck(args: Seq[String]): Unit = {
  val credId = args.headOption match {
    case None =>
      Console.err.println("Missing positional argument: ID")
      return
    case Some(id) => id
  }

  val loaded = credentials.verboseLoadCredfile() getOrElse sys.exit(1)

  loaded.get(credId) match {
    case Some(_) =>
      println(s"Credentials found for id: $credId")
    case None =>
      Console.err.println(s"No credentials found with id: $credId")
      sys.exit(1)
  }
}

def credSet(args: Seq[String]): Unit = {
  val credId = args.headOption match {
    case None =>
      Console.err.println("Missing positional argument: ID")
      sys.exit(1)
    case Some(id) => id
  }

  val loaded = credentials.verboseLoadCredfile() getOrElse sys.exit(1)

  val (user, encPassword) = {
    // Java/Scala Consoles fail in Cygwin
    val reader = org.jline.reader.LineReaderBuilder.builder().terminal(
      org.jline.terminal.TerminalBuilder.builder().build()
    ).build()

    (
      reader.readLine("user: "),
      base64.encode(reader.readLine("password: ", 0.toChar))
    )
  }

  val cred = credentials.Creds(user = user, encPassword = encPassword)

  val updated = loaded.updated(credId, cred)
  Files.write(
    credentials.credfile,
    upickled.write(updated, indent = 4).getBytes("UTF-8")
  )
}

def credRm(args: Seq[String]): Unit = {
  val credId = args.headOption match {
    case None =>
      Console.err.println("Missing positional argument: ID")
      sys.exit(1)
    case Some(id) => id
  }

  val loaded = credentials.verboseLoadCredfile() getOrElse sys.exit(1)
  val updated = loaded.filterKeys(_ != credId)
  Files.write(
    credentials.credfile,
    upickled.write(updated, indent = 4).getBytes("UTF-8")
  )
}

def credVerify(args: Seq[String]): Unit = {
  val repofile = credentials.verboseLoadRepofile() getOrElse sys.exit(1)
  if (repofile.isEmpty)
    Console.err.println("Warning: repofile missing or empty.")
  val credfile = credentials.verboseLoadCredfile() getOrElse sys.exit(1)
  credentials.verboseResolveRepofile(credfile, repofile) match {
    case None => sys.exit(1)
    case Some(_) => println("All OK.")
  }
}

@main def main(args: String*): Unit = args match {
  case Seq() =>
    Console.err.println("No command specified.")
    sys.exit(1)
  case Seq("cred", rest @ _*) => rest match {
    case Seq() =>
      Console.err.println("No cred subcommand.")
      Console.err.println("Available subcommands: get set check")
      sys.exit(1)
    case Seq(sub, subargs @ _*) => sub match {
      case "check" => credCheck(subargs)
      case "set" => credSet(subargs)
      case "rm" => credRm(subargs)
      case "verify" => credVerify(subargs)
      case other =>
        Console.err.println(s"Unrecognized cred subcommand: $other")
        Console.err.println("Available subcommands: check set rm verify")
        sys.exit(1)
    }
  }

  case helps if helps.contains("-h") || helps.contains("--help") =>
    coursier.cli.Coursier.main(helps.toArray)

  case Seq(cmd, subargs @ _*) =>
    val resolved =
      credentials.verboseResolveRepofile() getOrElse { return }

    val coursierArgs =
      Seq(cmd) ++
      resolved.flatMap { v => Seq("--repository", v.repoRef) } ++
      subargs

    coursier.cli.Coursier.main(coursierArgs.toArray)
}
