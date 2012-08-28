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
package com.google.devtools.javatools.transform.scrubber;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import com.google.devtools.javatools.transform.SourceTransformer;

import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.Set;

/**
 * Unit tests for {@link StrippingRangeRecorder}.
 */
public class StrippingRangeRecorderTest extends TestCase {
  private static final Set<String> DEFAULT_STRIP_ANNOTATION_CLASSES =
      ImmutableSet.of("GwtIncompatible");
  private static final Set<String> DEFAULT_INCLUDE_ANNOTATION_CLASSES =
      ImmutableSet.of();
  
  private final RangeSet<Integer> expectedRemoved = TreeRangeSet.create();

  public void testTopLevelClass_notStripped() throws Exception {
    doTest("class FooClass {}");
  }

  public void testTopLevelClass_notStripped_withPackageStatement() throws Exception {
    doTest(padAndJoin(
        "package com.google.foo;",
        "",
        "class FooClass {",
        "",
        "}"));
  }

  public void testTopLevelClass_notStripped_withImportStatement() throws Exception {
    doTest(padAndJoin(
        "package com.google.foo;",
        "",
        "import com.google.bar.Bar;",
        "",
        "class FooClass {",
        "  void Bar() {}",
        "}"));
  }

  public void testTopLevelClass_stripped() throws Exception {
    // TODO(hhchan): Bug in javac?  Expect to start from 0 instead.
    expectedRemoved.add(Range.closedOpen(50, 151));

    doTest(padAndJoin(
        "@GwtIncompatible", // [0, 50)
        "class FooClass {",
        "  void Bar() {}",
        "}")); // [50, 200)
  }

  public void testTopLevelClass_stripped_withJavadoc() throws Exception {
    // TODO(hhchan): Bug in javac?  Expect to start from 0 instead.
    expectedRemoved.add(Range.closedOpen(200, 301));

    doTest(padAndJoin(
        "/**",
        " * @author hhchan",
        " */",
        "@GwtIncompatible", // [150, 200)
        "class FooClass {",
        "  void Bar() {}",
        "}")); // [200, 350), ends at 301
  }

  public void testSingleMethodInClass_stripped() throws Exception {
    expectedRemoved.add(Range.closedOpen(50, 200));

    doTest(padAndJoin(
        "class FooClass {",  // pos [0, 50)
        "  @GwtIncompatible",
        "  void Bar() {",
        "  }",  // pos [50, 200)
        "}")); // [200, 250)
  }

  public void testSingleMethodInClass_annotationInLined_stripped() throws Exception {
    expectedRemoved.add(Range.closedOpen(50, 150));

    doTest(padAndJoin(
        "class FooClass {",  // pos [0, 50)
        "  @GwtIncompatible void Bar() {",
        "  }",  // pos [50, 150)
        "}")); // [150, 200)
  }

  public void testSingleMethodInClass_stripped_withMultilineJavadoc() throws Exception {
    expectedRemoved.add(Range.closedOpen(50, 350));

    doTest(padAndJoin(
        "class FooClass {",  // pos [0, 50)
        "  /**",
        "   * Returns bar.",
        "   */",  // pos [50, 200)
        "  @GwtIncompatible",
        "  void Bar() {",
        "  }",  // pos [200, 350)
        "}"));
  }


  public void testSingleMethodInClass_stripped_withBlockComments() throws Exception {
    expectedRemoved.add(Range.closedOpen(200, 350));

    doTest(padAndJoin(
        "class FooClass {",  // pos [0, 50)
        "  /*",
        "   * block comment, not javadoc.",
        "   */",  // pos [50, 200), should not be stripped.
        "  @GwtIncompatible",
        "  void Bar() {",
        "  }",  // pos [200, 350)
        "}"));
  }

  public void testSingleMethodInClass_stripped_withMultilineJavadoc_notStartWithStar()
      throws Exception {
    expectedRemoved.add(Range.closedOpen(50, 350));

    doTest(padAndJoin(
        "class FooClass {",  // pos [0, 50)
        "  /**",
        "    Returns bar, not start with *.",
        "   */",  // pos [50, 200)
        "  @GwtIncompatible",
        "  void Bar() {",
        "  }",  // pos [200, 350)
        "}"));
  }

  public void testSingleMethodInClass_stripped_withMultilineJavadocAndLineComments()
      throws Exception {
    expectedRemoved.add(Range.closedOpen(50, 400));

    doTest(padAndJoin(
        "class FooClass {",  // pos [0, 50)
        "  /**",
        "   * Returns bar.",
        "   */",  // pos [50, 200)
        "  // TODO(hhchan): remove this.", // pos [200, 250)
        "  @GwtIncompatible",
        "  void Bar() {",
        "  }",  // pos [250, 400), both comments and javadoc are stripped.
        "}"));
  }

  public void testSingleMethodInClass_stripped_withSingleLineJavadoc() throws Exception {
    expectedRemoved.add(Range.closedOpen(50, 250));

    doTest(padAndJoin(
        "class FooClass {",  // pos [0, 50)
        "  /** Returns bar. */", // pos [50, 100)
        "  @GwtIncompatible",
        "  void Bar() {",
        "  }",  // pos [100, 250)
        "}")); // [250, 300)
  }

  public void testSingleMethodInClass_stripped_withSingleLineJavadocAndLineComments()
      throws Exception {
    expectedRemoved.add(Range.closedOpen(50, 300));

    doTest(padAndJoin(
        "class FooClass {",  // pos [0, 50)
        "  /** Returns bar. */",
        "  // TODO(hhchan): Remove this class.",  // pos [50, 150)
        "  @GwtIncompatible",
        "  void Bar() {",
        "  }",  // pos [150, 300), both comments and the method is stripped.
        "}"));
  }

  public void testMultipleMethodInClass_oneStripped_withSingleLineJavadoc() throws Exception {
    expectedRemoved.add(Range.closedOpen(50, 300));

    doTest(padAndJoin(
        "class FooClass {",
        "",
        "  /** Returns bar. */",
        "  @GwtIncompatible",
        "  void Bar() {",
        "  }",  // pos [100, 300), strip the leading blank line as well.
        "",
        "  /** Returns baz. */",
        "  void Baz() {",
        "  }",
        "}"));
  }

  public void testMultipleMethodInClass_multipleStripped_withSingleLineJavadoc() throws Exception {
    expectedRemoved.add(Range.closedOpen(50, 500));

    doTest(padAndJoin(
        "class FooClass {",
        "",
        "  /** Returns bar. */",
        "  @GwtIncompatible",
        "  void shouldBeStripped1() {",
        "  }",  // pos [100, 300), strip the leading blank line as well.
        "",
        "  @GwtIncompatible",
        "  void shouldBeStripped2() {",
        "  }",  // pos [350, 500), strip the leading blank line as well.
        "",
        "  /** Returns baz. */",
        "  void shouldNotBeStripped() {",
        "  }",
        "}"));
  }

  public void testMultipleMethodInClass_withBlockComments() throws Exception {
    expectedRemoved.add(Range.closedOpen(400, 850));

    doTest(padAndJoin(
        "class FooClass {",
        " /** counter. */",
        " int shouldNotBeStripped = 0;", // pos [50, 150)
        "",
        " /*",
        "  * random comments",
        "  * that isn't a javadoc.",
        "  */", // pos [200, 400)
        "",
        "  /** Returns bar. */",
        "  @GwtIncompatible",
        "  void shouldBeStripped1() {",
        "  }",  // pos [450, 650), strip the leading blank line as well.
        "",
        "  @GwtIncompatible",
        "  void shouldBeStripped2() {",
        "  }",  // pos [700, 850), strip the leading blank line as well.
        "",
        "  /** Returns baz. */",
        "  void shouldNotBeStripped() {",
        "  }",
        "}"));
  }

  public void testNestedClass_single_stripped() throws Exception {
    expectedRemoved.add(Range.closedOpen(300, 400));

    doTest(padAndJoin(
        "package com.google.foo;",
        "",
        "import com.google.bar.Bar;",
        "",
        "class FooClass {",
        "  void Bar() {}",
        "  @GwtIncompatible static class Stripped {",
        "  }",  // pos [300, 400)
        "",
        "  void Baz() {}",
        "}"));
  }

  public void testNestedClass_single_stripped_withInlinedJavadoc() throws Exception {
    expectedRemoved.add(Range.closedOpen(300, 400));

    doTest(padAndJoin(
        "package com.google.foo;",
        "",
        "import com.google.bar.Bar;",
        "",
        "class FooClass {",
        "  void Bar() {}",
        "  /** doc */ @GwtIncompatible static class F {",
        "  }",  // pos [300, 400)
        "",
        "  void Baz() {}",
        "}"));
  }

  public void testNestedClass_single_stripped_withSingleLinedJavadoc() throws Exception {
    expectedRemoved.add(Range.closedOpen(300, 450));

    doTest(padAndJoin(
        "package com.google.foo;",
        "",
        "import com.google.bar.Bar;",
        "",
        "class FooClass {",
        "  void Bar() {}",
        "  /** doc */",
        "  @GwtIncompatible static class F {",
        "  }",  // pos [300, 450)
        "",
        "  void Baz() {}",
        "}"));
  }

  public void testNestedClass_single_stripped_withMultiLinedJavadoc() throws Exception {
    expectedRemoved.add(Range.closedOpen(300, 550));

    doTest(padAndJoin(
        "package com.google.foo;",
        "",
        "import com.google.bar.Bar;",
        "",
        "class FooClass {",
        "  void Bar() {}",
        "  /** ",
        "   * doc ",
        "   */",
        "  @GwtIncompatible static class F {",
        "  }",  // pos [300, 550)
        "",
        "  void Baz() {}",
        "}"));
  }


  public void testNestedClass_multiple_someStripped_withMultiLinedJavadoc() throws Exception {
    expectedRemoved.add(Range.closedOpen(300, 650));
    expectedRemoved.add(Range.closedOpen(850, 1150));

    doTest(padAndJoin(
        "package com.google.foo;",
        "",
        "import com.google.bar.Bar;",
        "",
        "class FooClass {",
        "  void Bar() {}",
        "  /** ",
        "   * doc ",
        "   */",
        "  @GwtIncompatible static class F extends Bar {",
        "  }",  // pos [300, 550)
        "",
        "  @GwtIncompatible class G {}",  // pos [600, 650)
        "",
        "  void Baz() {}",
        "",
        " class H {",
        "",
        "   /** Another method to skipped. */",
        "   // TODO(hhchan): to be deprecated.",
        "   @GwtIncompatible",
        "   int skipped() {",
        "   }",  // pos [900, 1150)
        " }",
        "}"));
  }

  public void testField_single_noJavadoc() throws Exception {
    expectedRemoved.add(Range.closedOpen(150, 250));

    doTest(padAndJoin(
        "package com.google.foo;",
        "",
        "class FooClass {",
        "",
        "  @GwtIncompatible private int F = 100;",  // [200, 250)
        "",
        "}"));
  }

  public void testField_single_inlinedJavadoc() throws Exception {
    expectedRemoved.add(Range.closedOpen(150, 250));

    doTest(padAndJoin(
        "package com.google.foo;",
        "",
        "class FooClass {",
        "",
        "  /** d */ @GwtIncompatible private int F = 100;",  // [200, 250)
        "",
        "}"));
  }

  public void testField_single_multilinedJavadoc() throws Exception {
    expectedRemoved.add(Range.closedOpen(150, 450));

    doTest(padAndJoin(
        "package com.google.foo;",
        "",
        "class FooClass {",
        "",
        "  /**",
        "   * javadoc line 1",
        "   * javadoc line 2",
        "   */",
        "  @GwtIncompatible private int F = 100;",  // [200, 450)
        "",
        "  public double toBeKept = 12.0;",
        "",
        "}"));
  }

  public void testInclude_singleClass() throws Exception {
    doTest(padAndJoin(
        "package com.google.foo;",
        "",
        "import com.google.bar.Bar;",
        "",
        "@Include",
        "class FooClass {",
        "  void Bar() {}",
        "",
        "  void Baz() {}",
        "}"),
        expectLinesRemoved(4, 5),
        DEFAULT_STRIP_ANNOTATION_CLASSES,
        ImmutableSet.of("Include"));
  }

  public void testInclude_singleClass_staticAnnotationClass() throws Exception {
    doTest(padAndJoin(
        "package com.google.foo;",
        "",
        "import com.google.bar.Bar;",
        "",
        "@OuterClass.Include",
        "class FooClass {",
        "  void Bar() {}",
        "",
        "  void Baz() {}",
        "}"),
        expectLinesRemoved(4, 5),
        DEFAULT_STRIP_ANNOTATION_CLASSES,
        ImmutableSet.of("OuterClass.Include"));
  }

  public void testInclude_singleClass_excludedMemberInline() throws Exception {
    doTest(padAndJoin(
        "package com.google.foo;",
        "",
        "import com.google.bar.Bar;",
        "",
        "@Include",
        "class FooClass {",
        "  void Bar() {}",
        "",
        "  @GwtIncompatible void Baz() {}",
        "}"),
        expectLinesRemoved(4, 5, 8, 9),
        DEFAULT_STRIP_ANNOTATION_CLASSES,
        ImmutableSet.of("Include"));
  }

  public void testInclude_singleClass_redundantIncludeMember() throws Exception {
    doTest(padAndJoin(
        "package com.google.foo;",
        "",
        "import com.google.bar.Bar;",
        "",
        "@Include",
        "class FooClass {",
        "  void Bar() {}",
        "",
        "  @Include",
        "  void Baz() {}",
        "}"),
        expectLinesRemoved(4, 5, 8, 9),
        DEFAULT_STRIP_ANNOTATION_CLASSES,
        ImmutableSet.of("Include"));
  }

  public void testInclude_singleClass_excludedMemberSeparateLine() throws Exception {
    doTest(padAndJoin(
        "package com.google.foo;",
        "",
        "import com.google.bar.Bar;",
        "",
        "@Include",
        "class FooClass {",
        "  void Bar() {}",
        "",
        "  @GwtIncompatible",
        "  void Baz() {}",
        "}"),
        expectLinesRemoved(4, 5, 8, 9, 10),
        DEFAULT_STRIP_ANNOTATION_CLASSES,
        ImmutableSet.of("Include"));
  }

  public void testInclude_includeInExclude() throws Exception {
    doTest(padAndJoin(
        "package com.google.foo;",
        "",
        "import com.google.bar.Bar;",
        "",
        "@Exclude",
        "class FooClass {",
        "  void Bar() {}",
        "",
        "  @Include",
        "  void Baz() {}",
        "}",
        ""),
        expectLinesRemoved(4, 5, 6, 7, 8, 9, 10, 11),  // should remove entire class
        ImmutableSet.of("Exclude"),
        ImmutableSet.of("Include"));
  }

  public void testInclude_oneClassIncluded_oneClassExcluded() throws Exception {
    doTest(padAndJoin(
        "package com.google.foo;",
        "",
        "import com.google.bar.Bar;",
        "",
        "@Include",
        "class IncludedClass {",
        "  void Bar() {}",
        "}",
        "",
        "class ExcludedClass {",
        "  void Bar() {}",
        "}",
        ""),
        expectLinesRemoved(4, 5, 9, 10, 11, 12),
        DEFAULT_STRIP_ANNOTATION_CLASSES,
        ImmutableSet.of("Include"));
  }

  public void testInclude_includeAndExclude() throws Exception {
    doTest(padAndJoin(
        "package com.google.foo;",
        "",
        "import com.google.bar.Bar;",
        "",
        "@Include",
        "public class Foo {",
        "  public Foo() {}",
        "  int getCount() { return 0; }",
        "  @Exclude int getCount2() { return 1; }",
        "",
        "  private static class IncludedInner {",
        "    @UnrelatedAnnotation int x;",
        "    @StaticAnnotations.Exclude int y;",
        "  }",
        "",
        "  @StaticAnnotations.Exclude(param = \"true\")",
        "  private static class ExcludedInner {",
        "    int w;",
        "    @Include int shouldntBeIncluded;",
        "  }",
        "}",
        "",
        "class Confidential {",
        "  @Include int shouldntBeIncluded;",
        "}",
        ""),
        expectLinesRemoved(4, 5, 9, 13, 15, 16, 17, 18, 19, 20, 22, 23, 24, 25),
        ImmutableSet.of("Exclude", "StaticAnnotations.Exclude"),
        ImmutableSet.of("Include"));
  }

  private void doTest(String content) throws Exception {
    doTest(
        content,
        expectedRemoved,
        DEFAULT_STRIP_ANNOTATION_CLASSES,
        DEFAULT_INCLUDE_ANNOTATION_CLASSES);
  }

  private void doTest(
      String content,
      RangeSet<Integer> expectedRemoved,
      Set<String> stripAnnotations,
      Set<String> includeAnnotations) throws Exception {
    final JCCompilationUnit jcCompilationUnit
        = (JCCompilationUnit) SourceTransformer.parse(content);

    final StrippingRangeRecorder recorder = new StrippingRangeRecorder(
        content, stripAnnotations, includeAnnotations);
    jcCompilationUnit.accept(recorder);

    assertEquals("rangesToRemove", expectedRemoved, recorder.getRangesToRemove());
  }

  /**
   * Pads each line to 50 characters (including the newline character), so that
   * it's easier to derive the position from the line number.  Also adds a
   * newline to the last line.
   */
  private static String padAndJoin(String... lines) {
    return Joiner.on('\n').join(Lists.transform(Arrays.asList(lines),
        new Function<String, String>() {
          @Override public String apply(String line) {
            checkArgument(
                line.length() < 50, "[%s] is too long, shouldn't be longer than 49 chars.", line);
            return Strings.padEnd(line, 49, ' ');  // 49 char + newline == 50 chars per line.
          }
        })) + "\n";  // the last line is also prepended with '\n'
  }

  /**
   * Per padAndJoin(), returns the RangeSet corresponding to the given line removals, e.g.:
   *   - lines 2 and 3 -> { [50, 150) }
   *   - lines 4 and 8 -> { [150, 200), [350, 400) }
   */
  private static RangeSet<Integer> expectLinesRemoved(int... linesRemoved) {
    RangeSet<Integer> ranges = TreeRangeSet.create();
    for (int line : linesRemoved) {
      ranges.add(Range.closedOpen((line - 1) * 50, line * 50));
    }
    return ranges;
  }
}
