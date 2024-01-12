#!/bin/bash
#
#  Copyright 2023 The original authors
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#

SN_VERSION=${1:-"0.5.0-SNAPSHOT"}

source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk use java 21.0.1-graal 1>&2

scala-cli --power package ./scala -f --assembly -o ./scala/calculate_average_lbialy.jar
scala-cli --power package ./scala -f --native --native-mode release-full --native-version $SN_VERSION -o ./scala/calculate_average_lbialy_native
