package androidx.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Desktop stand-in for androidx.annotation.GuardedBy. It is a documentation-only concurrency
// annotation; keeping the type lets the core source compile unchanged off the Android classpath.
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.TYPE,
        ElementType.PARAMETER, ElementType.LOCAL_VARIABLE})
public @interface GuardedBy {
    String value();
}
