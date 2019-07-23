package com.wavefront.sdk.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.annotation.Nonnull;
import javax.annotation.meta.TypeQualifierDefault;

/**
 * A common annotation to declare that parameters and return values
 * are to be considered as non-nullable by default for a given package.
 * <p>
 * Annotate package declaration in "package-info.java" files, then it
 * sets the non-nullable semantics to the package.
 *
 * @author Tadaya Tsuyukubo
 */
@Target(ElementType.PACKAGE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Nonnull
@TypeQualifierDefault({ElementType.METHOD, ElementType.PARAMETER})
public @interface NonNullApi {
}
