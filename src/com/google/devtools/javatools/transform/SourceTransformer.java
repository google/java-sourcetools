package com.google.devtools.javatools.transform;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

/**
 * Coordinates transformations on Java source files. Given a set of Java source files, applies
 * a list of {@link SourceTransformTask}s on the files, and writes the transformed files back
 * out to the filesystem.
 */
public class SourceTransformer {
  /**
   * Options to pass to javac when parsing sources only.
   */
  private static final Iterable<String> PARSING_OPTIONS = ImmutableList.of(
      "-proc:none");    // Saves 0.5ms creating each JavacTask.
  
  private final Function<String, String> outputFileFunction;
  private final ImmutableList<SourceTransformTask> tasks;
  
  /**
   * Creates a SourceTransformer.
   *
   * @param outputFileFunction a function which produces an output file path, given the
   *     input file path
   * @param tasks tasks to apply to the source
   */
  public SourceTransformer(Function<String, String> outputFileFunction,
      List<SourceTransformTask> tasks) {
    this.outputFileFunction = outputFileFunction;
    this.tasks = ImmutableList.copyOf(tasks);
  }
  
  /**
   * Applies source transform {@literal task}s to each file in {@literal paths}, writing output
   * files for each one according to the {@literal outputFileFunction}.
   *
   * @throws IOException if a filesystem error occurs
   * @throws JavaParseException if source failed to parse
   */
  public void transform(Iterable<String> paths) throws IOException {
    for (String path : paths) {
      String fileContent = Files.toString(new File(path), Charsets.UTF_8);
      for (SourceTransformTask task : tasks) {
        CompilationUnitTree tree = parse(fileContent);
        fileContent = task.transform(tree, fileContent);
      }
      
      File outFile = new File(outputFileFunction.apply(path));
      Files.createParentDirs(outFile);
      Files.write(fileContent, outFile, Charsets.UTF_8);
    }
  }

  /**
   * Parses Java source code, and returns the compilation tree.
   */
  @VisibleForTesting
  public static CompilationUnitTree parse(final String source) throws IOException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    JavaFileObject file = new SimpleJavaFileObject(
        URI.create("string://virtual-source-file.java"), JavaFileObject.Kind.SOURCE) {
      @Override
      public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return source;
      }
    };
    JavacTask parseTask = (JavacTask) compiler.getTask(null, null, 
        FailOnErrorDiagnosticListener.INSTANCE, PARSING_OPTIONS, null, ImmutableList.of(file));
    return Iterables.getOnlyElement(parseTask.parse());
  }

  private static enum FailOnErrorDiagnosticListener implements DiagnosticListener<JavaFileObject> {
    INSTANCE;
    
    @Override
    public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
      if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
        throw new JavaParseException(diagnostic);
      }
    }
  }
}
