#!/bin/sh
D="$(dirname "$0")/.tcl"
W="$D/dependencies/libs/webp-0.2.0.jar"
if [ -f "$W" ]; then exec java "-Dtcraft.dir=$D" -cp "$0:$W" tcraft.Boot "$@"; fi
exec java "-Dtcraft.dir=$D" -jar "$0" "$@"
