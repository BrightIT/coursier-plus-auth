#!/usr/bin/env bash

SCRIPTDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
TARGET=$SCRIPTDIR/../jars

scalav=$1
shift

ammv=$1
shift

try() {
  "$@" || exit
}

fname="$TARGET/$scalav-$ammv.jar"

[[ ! -f "$fname" ]] && try curl -L -o "$fname" \
  "https://github.com/lihaoyi/ammonite/releases/download/$ammv/$scalav-$ammv"

args=()

[[ -n $COURSIERPLUSAUTH_NO_REMOTE_TRACKING ]] && args+=(--no-remote-tracking)

args+=("$@")

exec java -noverify -jar "$fname" "${args[@]}"
