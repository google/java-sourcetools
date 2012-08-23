# Copyright 2012 Google Inc. All Rights Reserved.
# Author: bduff@google.com (Brian Duff)
#
# Description:
# A source transform task that can scrub annotated declarations
# from java source.

package(default_strict_java_deps = 1)

java_library(
    name = "scrubber",
    srcs = glob([ "*.java" ]),
    deps = [
        "//java/com/google/common/annotations",
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/common/io",
        "//java/com/google/devtools/javatools/transform",
        "//third_party/java/jdk:langtools",
    ],
    visibility = [
        "//java/com/google/devtools/moe/scrubber:__pkg__",
    ])
