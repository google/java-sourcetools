/*
 * Copyright 2012 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.devtools.javatools.transform;

import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;

import java.util.Collections;
import java.util.List;

/**
 * String utilities.
 */
public class StringUtil {
  private StringUtil() {}
  
  /**
   * Strips the given ranges from {@code text}.
   */
  public static final String stripRanges(String text, RangeSet<Integer> rangesToRemove) {
    StringBuilder contentBuilder = new StringBuilder(text);
    // Delete the ranges.  Go from last to first to avoid having to
    // compute the offsets.
    List<Range<Integer>> ranges = Lists.newArrayList(rangesToRemove.asRanges());
    Collections.reverse(ranges);
    for (Range<Integer> range : ranges) {
      contentBuilder.delete(range.lowerEndpoint(), range.upperEndpoint());
    }
    return contentBuilder.toString();
  }

}
