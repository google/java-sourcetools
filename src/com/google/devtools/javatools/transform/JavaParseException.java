package com.google.devtools.javatools.transform;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

/**
 * Thrown when parsing of java source fails.
 */
public class JavaParseException extends RuntimeException {
  JavaParseException(Diagnostic<? extends JavaFileObject> diagnostic) {
    super(diagnostic.toString());
  }
}
