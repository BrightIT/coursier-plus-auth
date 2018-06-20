# coursier+auth

`coursier+auth` is a quick'n'dirty wrapper around Coursier for authorization.
It allows keeping repositories for Ammonite and Coursier in a single repofile,
while keeping credentials outside of VCS.

Additionally, since Coursier is launched from Ammonite script, standard Unix
proxy environment variables are automatically respected, the same as in Ammonite.

# Quick start

This assumes you're already familiar with Coursier and Ammonite.

Sample usage:

```bash
> ls ./bin
amm+auth  coursier+auth
> export PATH=$PATH:$PWD/bin
> mkdir .coursier+auth && cat > .coursier+auth/repofile.json
[
  ["foo-corp", "http://", "foo-corp.net/repository/releases"]
]
> coursier+auth cred verify
Errors when resolving repofile:
  No credentials for repo `http://foo-corp.net/repository/releases` (id: foo-corp)

To set credentials for id ID, use:
$ coursier+auth cred set $ID
> # We have a repository reference, but we need to set credentials for it
> coursier+auth cred set foo-corp
user: user
password:
> coursier+auth launch net.foo-corp:hello-world:1.0.0
Hello, world!
> cat script.sc
import $ivy.`net.foo-corp:hello-world:1.0.0`
println("Hello, world!")
> amm+auth 2.12 1.1.0 script.sc
Hello, world!
```

For more detailed help on `coursier+auth` and `amm+auth`, try `--help`
and see their contents.

# Repofile

`amm+auth` and `coursier+auth` both load `.coursier+auth/repofile.json`
from the first parent directory containing `.coursier+auth` directory.
The repofile contains repository addresses, but no credentials.
Credentials need to be added using `coursier+auth cred set $ID`.

Three types of entries are supported:
```json
[
  ["foo-corp", "http://", "foo-corp.net/repository/releases"],
  "https://repository.apache.org/content/repositories/releases/",
  "~/.m2/repository"
]
```

The first one is a private repository requiring credentials with id `foo-corp`.
Second and third array members are respectively parts of pseudo-URL
before and after credentials, as accepted by Coursier CLI `--repository`.

The second one is simply a public repository. Again, all pseudo-URLs accepted
by Coursier CLI are supported.

The third one is a local path beginning with `~` - they are special-cased to
resolve to corresponding `file://` URL.

# Ammonite interactive usage

`coursier+auth` exposes loaded credentials to Ammonite scripts. Note that
since they are contained inside `coursier.Repository`, they would be available
anyway under `interp.repositories()` - `coursier+auth` just exposes them
in a nicer format. See `coursierplusauth.sc` for more info.

# Notes

Note that `amm+auth` relies on custom Ammonite home contained inside this repo.
Among other things, this means different history & cache from normal Ammonite.

If you want to use `amm+auth` with your own home predefs, you'll need to include
the `coursierplusauth.sc` file in them.
