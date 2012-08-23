// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.devtools.javatools.transform.scrubber;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;

import com.sun.source.tree.LineMap;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCModifiers;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.TreeScanner;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Scanner of the compilation unit tree that records the position ranges to
 * remove for stripping annotations.  The removal is line-based: a line is either completely
 * retained or completely removed.
 *
 * @author hhchan@google.com (Hayward Chan)
 */
class StrippingRangeRecorder extends TreeScanner {

  private final CharSequence fileContent;
  private final Set<String> stripAnnotationClasses;
  private final Set<String> includeAnnotationClasses;
  private final RangeSet<Integer> rangesToRemove = TreeRangeSet.create();

  private JCCompilationUnit compilationUnit;
  private LineMap lineMap;
  private int lastLine;
  /**
   * Whether code traversal is currently inside an included class. We need this to determine whether
   * to include or exclude an inner class.
   */
  private boolean insideClass = false;

  StrippingRangeRecorder(
      CharSequence fileContent, Set<String> stripAnnotations, Set<String> includeAnnotations) {
    this.fileContent = checkNotNull(fileContent, "fileContent");
    this.stripAnnotationClasses = checkNotNull(stripAnnotations, "stripAnnotations");
    this.includeAnnotationClasses = checkNotNull(includeAnnotations, "includeAnnotations");
  }

  @Override
  public void visitTopLevel(JCCompilationUnit compilationUnit) {
    this.compilationUnit = checkNotNull(compilationUnit, "compilationUnit");
    this.lineMap = compilationUnit.getLineMap();
    this.lastLine = (int) lineMap.getLineNumber(fileContent.length() - 1);
    super.visitTopLevel(this.compilationUnit);
  }

  @Override
  public void visitMethodDef(JCMethodDecl method) {
    if (!stripElement(method, method.getModifiers())) {
      super.visitMethodDef(method);
    }
  }

  @Override
  public void visitClassDef(JCClassDecl clazz) {
    if (!stripElement(clazz, clazz.getModifiers())) {
      boolean oldInsideClass = insideClass;
      insideClass = true;
      super.visitClassDef(clazz);
      insideClass = oldInsideClass;
    }
  }

  @Override
  public void visitVarDef(JCVariableDecl var) {
    if (!stripElement(var, var.getModifiers())) {
      super.visitVarDef(var);
    }
  }

  RangeSet<Integer> getRangesToRemove() {
    return rangesToRemove;
  }

  private void removeElement(JCTree tree) {
    int startPos = tree.getStartPosition();
    /*
     * The end position of this element, exclusive.  Move to the beginning
     * of the next line if possible.
     */
    int endPos = tree.getEndPosition(compilationUnit.endPositions);
    final int endPosLineNumber = (int) lineMap.getLineNumber(endPos);
    if (endPosLineNumber < lastLine) {
      endPos = (int) lineMap.getStartPosition(endPosLineNumber + 1);
    }

    /*
     * If this element has javadoc, backtrack and skip it.  The javadoc is
     * identified by a regex derived from the content.
     */
    if (compilationUnit.docComments.containsKey(tree)) {
      startPos = backtrackForJavadoc(tree, startPos);
    }
    /*
     * Strip the leading blank lines as well.
     */
    startPos = backtraceForLeadingWhitespace(startPos);
    /*
     * Move the startingPos to the beginning of line to avoid trailing whitespace
     * of the previous remaining program elements.
     */
    startPos = (int) lineMap.getStartPosition(lineMap.getLineNumber(startPos));
    rangesToRemove.add(Range.closedOpen(startPos, endPos));
  }

  private int backtrackForJavadoc(JCTree tree, int startPos) {
    final String docComment = compilationUnit.docComments.get(tree);
    final StringBuffer docRegex = new StringBuffer(".*" + Pattern.quote("/**"));
    for (String line : Splitter.on("\n").split(docComment)) {
      docRegex.append("[\\s]*")  // each line can optionally start with whitespaces.
          /*
           * The starting '*' is optional.  javadoc doesn't require each
           * line to with '*'.  One-liner javadoc also doesn't have the
           * leading '*'.
           */
          .append(Pattern.quote("*") + "?")
          .append(Pattern.quote(line));
    }
    docRegex
        /*
         * The final * is optional.  Single-line javadoc needs it, but
         * multiline javadoc doesn't have it (the '*' on the last line accounts
         * for the '*'.
         */
        .append(Pattern.quote("*") + "?")
        .append(Pattern.quote("/"))
        .append(".*");  // in case there are other comments (line or block) after this.
    final Pattern docPattern
        = Pattern.compile(docRegex.toString(), Pattern.MULTILINE | Pattern.DOTALL);

    int commentStartPos = (int) lineMap.getStartPosition(lineMap.getLineNumber(startPos));
    boolean matched
        = docPattern.matcher(fileContent.subSequence(commentStartPos, startPos)).matches();
    while (!matched && commentStartPos > 0) {
      /*
       * Keep going back, one line at a time, until we match the full javadoc.
       * The 'commentStartPos > 0' sholdn't be necessary unless we have a bug.
       */
      commentStartPos =
          (int) lineMap.getStartPosition(lineMap.getLineNumber(commentStartPos) - 1);
      matched = docPattern.matcher(fileContent.subSequence(commentStartPos, startPos)).matches();
    }
    checkState(matched, "Cannot find the javadoc [%s] for [%s] (pattern [%s])",
        docComment, tree, docPattern.pattern());
    return commentStartPos;
  }

  private int backtraceForLeadingWhitespace(int startPos) {
    int whiteSpaceLinesStartPos = startPos;
    boolean isWhitespaceLine = true;
    while (isWhitespaceLine && whiteSpaceLinesStartPos > 0) {
      int previousLineStart
          = (int) lineMap.getStartPosition(lineMap.getLineNumber(whiteSpaceLinesStartPos) - 1);
      /*
       * In principle, we should use the JLS definition of whitespace from
       * http://java.sun.com/docs/books/jls/third_edition/html/lexical.html#3.6 ... but we already
       * play fast and loose with the spec by requiring lines to be split by \n, by using regex \s
       * elsewhere for whitespace, and probably other stuff, and things work just fine.
       */
      isWhitespaceLine = CharMatcher.WHITESPACE.matchesAllOf(
          fileContent.subSequence(previousLineStart, whiteSpaceLinesStartPos));

      if (isWhitespaceLine) {
        whiteSpaceLinesStartPos = previousLineStart;
      }
    }
    return whiteSpaceLinesStartPos;
  }

  /**
   * Strips the given element from source if needed. Also strips its include annotations.
   *
   * @return whether the element itself was stripped
   */
  private boolean stripElement(JCTree element, JCModifiers modifiers) {
    for (JCAnnotation a : modifiers.getAnnotations()) {
      String annotationName = a.getAnnotationType().toString().trim();
      if (stripAnnotationClasses.contains(annotationName)) {
        removeElement(element);
        return true;
      } else if (includeAnnotationClasses.contains(annotationName)) {
        removeElement(a);
        return false;
      }
    }
    // Strip an un-annotated element iff it's an unincluded outer class.
    boolean isClass = (element.getKind() == Tree.Kind.CLASS);
    if (isClass && !insideClass && !includeAnnotationClasses.isEmpty()) {
      removeElement(element);
      return true;
    }
    return false;
  }
}
