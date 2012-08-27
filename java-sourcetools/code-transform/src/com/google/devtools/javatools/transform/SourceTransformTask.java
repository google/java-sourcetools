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
