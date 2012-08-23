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
