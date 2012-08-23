# Copyright 2012 Google Inc. All Rights Reserved.
# Author: bduff@google.com (Brian Duff)
#
# Description:
# Simple framework for transforming Java source code.

package(
    default_strict_java_deps = 1,
    default_visibility = [ "//visibility:public" ])

java_library(
    name = "transform",
    srcs = glob([ "*.java" ]),
    deps = [
        "//java/com/google/common/annotations",
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/common/flags",
        "//java/com/google/common/io",
        "//third_party/java/jdk:langtools",
    ])
