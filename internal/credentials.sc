import $file.base64

import java.nio.file._
import java.net.URLEncoder
import scala.io.Source
import upickle.{default => upickled}
import scala.util.{Try, Success, Failure}
import scala.util.control.NonFatal

type Credfile = Map[String, Creds]
type Repofile = List[Repo]

case class Creds(user: String, encPassword: String)
object Creds {
  implicit val rw: upickled.ReadWriter[Creds] = upickled.macroRW
}

case class VerifiedRepository(repoRef: String, repo: coursier.Repository)

sealed trait Repo
case class PublicRepo(repo: String) extends Repo
case class PrivateRepo(
  credId: String,
  repoPre: String,
  repoPost: String
) extends Repo

object Repo {
  implicit val publicRepoRW: upickled.ReadWriter[PublicRepo] =
    upickled.readwriter[String].bimap[PublicRepo](
      r => r.repo,
      u => PublicRepo(u)
    )
  implicit val privateRepoRW: upickled.ReadWriter[PrivateRepo] =
    upickled.readwriter[(String, String, String)].bimap[PrivateRepo](
      r => (r.credId, r.repoPre, r.repoPost),
      { case (id, repoPre, repoPost) => PrivateRepo(id, repoPre, repoPost) }
    )
  implicit val rw: upickled.ReadWriter[Repo] = new upickled.ReadWriter[Repo] {
    override val expectedMsg = "expected string or [string, string]"

    override def visitString(s: CharSequence, index: Int) =
      publicRepoRW.visitString(s, index)

    override def visitArray(index: Int) =
      privateRepoRW.visitArray(index)

    def write0[V](out: ujson.Visitor[_, V], v: Repo) = v match {
      case pub: PublicRepo => publicRepoRW.write0(out, pub)
      case priv: PrivateRepo => privateRepoRW.write0(out, priv)
    }
  }
}

lazy val credfile = {
  val f = (sys.env.get("COURSIERPLUSAUTH_CREDFILE") match {
    case Some(envvar) => Paths.get(envvar)
    case None => Paths.get(sys.props("user.home"), ".coursier+auth/credentials.json")
  }).toAbsolutePath
  Files.createDirectories(f.getParent)
  if (!Files.exists(f)) Files.write(f, "{}".getBytes("UTF-8"))
  f
}

lazy val repofile = {
  sys.env.get("COURSIERPLUSAUTH_REPOFILE") orElse {
    val wd = Paths.get("").toAbsolutePath
    Iterator.iterate(wd)(_.getParent)
      .takeWhile(_ != null)
      .map(_ resolve ".coursier+auth/repofile.json")
      .find(Files.exists(_))
      .map(_.toString)
    } getOrElse ".coursier+auth/repofile.json"
}

sealed trait CredentialException
final case class UpickleException(ex: Throwable) extends CredentialException
final case class CredentialIOException(ex: Throwable) extends CredentialException

def attempt[E, T](handler: Throwable => E)(thunk: => T) =
  try {
    val res = thunk
    Right(res)
  } catch {
    case NonFatal(ex) => Left(handler(ex))
  }

def loadCredfile(): Either[CredentialException, Credfile] =
  for {
    content <- attempt(CredentialIOException) {
      Source.fromFile(credfile.toFile).mkString
    }.right
    res <- attempt(UpickleException) {
      upickled.read[Map[String, Creds]](content)
    }.right
  } yield res

def loadRepofile(): Either[CredentialException, List[Repo]] =
  for {
    content <- attempt(CredentialIOException) {
      Source.fromFile(repofile).mkString
    }.right
    res <- attempt(UpickleException) {
      upickled.read[List[Repo]](content)
    }.right
  } yield res

def resolveRepofile(
  credmap: Map[String, Creds],
  repofile: List[Repo]
): Either[List[String], List[VerifiedRepository]] = {
  val errs = scala.collection.mutable.ListBuffer.empty[String]

  val verifieds = repofile.flatMap { repo =>
    val auth = repo match {
      case PrivateRepo(id, pre, post) =>
        credmap.get(id) match {
          case None =>
            errs += s"No credentials for repo `$pre$post` (id: $id)"
            None
          case Some(Creds(usr, encPwd)) =>
            try {
              Some(coursier.core.Authentication(usr, base64.decode(encPwd)))
            } catch {
              case NonFatal(ex) =>
                errs += s"Invalid base64 enc password: `$encPwd` (user: $usr)"
                None
            }
        }
      case PublicRepo(_) => None
    }

    def resolve(repo: String): String =
      if (repo startsWith "~")
        Paths.get(sys.props("user.home"), repo.tail).toUri.toString
      else repo

    def parse(repo: String) =
      coursier.CacheParse.repository(repo).toEither

    val verified = repo match {
      case PublicRepo(repo) =>
        val resolved = resolve(repo)
        parse(resolved).right.map(VerifiedRepository(resolved, _))
      case PrivateRepo(id, repoPre, repoPost) =>
        val authStr = auth match {
          case Some(coursier.core.Authentication(usr, pwd)) =>
            // TODO: I'm pretty sure this mangles spaces - use Apache Commons?
            val encUsr = URLEncoder.encode(usr)
            val encPwd = URLEncoder.encode(pwd)
            s"$encUsr:$encPwd@"
          case None =>
            ""
        }

        // try to fail w/o credentials in the string to hide them from output
        parse(repoPre + repoPost).right.flatMap { _ =>
          val repoRef = s"$repoPre$authStr$repoPost"
          parse(repoRef).right.map(VerifiedRepository(repoRef, _))
        }
    }

    verified match {
      case Left(err) =>
        errs += err
        None
      case Right(v) =>
        Some(v)
    }
  }

  if (errs.nonEmpty) Left(errs.result()) else Right(verifieds)
}

@inline def describeException[T](
  fileDescription: => String
)(
  e: Either[CredentialException, T]
): Option[T] = e.left.map { credEx =>
  val (description, ex) = credEx match {
    case CredentialIOException(ex) => ("reading", ex)
    case UpickleException(ex) => ("parsing", ex)
  }
  Console.err.println(s"Error when $description $fileDescription")
  Console.err.println(s"\t ${ex.getClass.getCanonicalName}: ${ex.getMessage}")
  ()
}.right.toOption

def verboseLoadCredfile(): Option[Credfile] =
  describeException(s"credfile $credfile")(loadCredfile())

def verboseLoadRepofile(): Option[Repofile] =
  if (Files.exists(Paths.get(repofile)))
    describeException(s"repofile $repofile")(loadRepofile())
  else Some(Nil)

def verboseResolveRepofile(
  credmap: Map[String, Creds],
  repofile: List[Repo]
): Option[List[VerifiedRepository]] =
  resolveRepofile(credmap, repofile) match {
    case Left(errs) =>
      Console.err.println("Errors when resolving repofile:")
      errs.foreach { e =>
        print("  ")
        Console.err.println(e)
      }
      Console.err.println()
      Console.err.println("To set credentials for id ID, use:")
      Console.err.println("$ coursier+auth cred set $ID")
      None
    case Right(r) => Some(r)
  }

def verboseResolveRepofile(): Option[List[VerifiedRepository]] = for {
  repofile <- verboseLoadRepofile()
  credfile <- verboseLoadCredfile()
  resolved <- verboseResolveRepofile(credfile, repofile)
} yield resolved
