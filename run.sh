#!/bin/sh
set -eu
cd -- "$(dirname -- "$0")"
mvn -q -o compile
exec java -cp target/classes accountledger.scenario.Scenario
