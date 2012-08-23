package com.google.devtools.javatools.transform.scrubber;

import com.google.common.collect.ImmutableSet;
import com.google.devtools.javatools.transform.SourceTransformTask;
import com.google.devtools.javatools.transform.StringUtil;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;

import java.util.Collection;

/**
 * Transformation task that strips class, method, and field declarations that are annotated 
 * with specified annotations (e.g. @InternalOnly).
 */
public class StripAnnotatedDeclarations implements SourceTransformTask {
  private final ImmutableSet<String> stripAnnotations;
  private final ImmutableSet<String> includeAnnotations;
  
  public StripAnnotatedDeclarations(Collection<String> stripAnnotations,
      Collection<String> includeAnnotations) {
    this.stripAnnotations = ImmutableSet.copyOf(stripAnnotations);
    this.includeAnnotations = ImmutableSet.copyOf(includeAnnotations);
  }
  
  @Override
  public String transform(CompilationUnitTree tree, String fileContent) {
    StrippingRangeRecorder rangeRecorder =
        new StrippingRangeRecorder(fileContent, stripAnnotations, includeAnnotations);
    ((JCCompilationUnit) tree).accept(rangeRecorder);
    return StringUtil.stripRanges(fileContent, rangeRecorder.getRangesToRemove());
  }
}
