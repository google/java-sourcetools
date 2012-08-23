package com.google.devtools.javatools.transform;

import com.sun.source.tree.CompilationUnitTree;

/**
 * Applies a transformation to a single Java source file.
 */
public interface SourceTransformTask {
  /**
   * Applies a transformation, given the parse tree and original source text of a Java source
   * file.
   * 
   * @param tree the parse tree of the original source
   * @param fileContent the text of the original source
   * @return the transformed source
   */
  String transform(CompilationUnitTree tree, String fileContent);
  
}
