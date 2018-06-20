import $file.internal.{credentials => _credentials}

val credentials = _credentials

val credfile = credentials.verboseLoadCredfile() getOrElse Map.empty
val repofile = credentials.verboseLoadRepofile() getOrElse Nil
val resolvedRepofile =
  credentials.verboseResolveRepofile(credfile, repofile) getOrElse Nil

interp.repositories() ++= resolvedRepofile.map(_.repo)
