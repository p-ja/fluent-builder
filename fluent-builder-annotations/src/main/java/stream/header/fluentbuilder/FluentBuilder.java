package stream.header.fluentbuilder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to generate a fluent builder for a record.
 * Fields marked with @Mandatory must be set before build() can be called.
 * Fields without @Mandatory are optional and can be set at any time.
 * Fields marked with @Ignore will not be included in the builder.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface FluentBuilder {

    /**
     * Marks a record component as mandatory in the fluent builder.
     * The builder will enforce that all mandatory fields are set before build() can be called.
     */
    @Target(ElementType.RECORD_COMPONENT)
    @Retention(RetentionPolicy.SOURCE)
    @interface Mandatory {
    }

    /**
     * Marks a record component to be ignored by the builder generator.
     * Fields marked with this annotation will not be included in the builder
     * and must be set through other means (e.g., computed values, defaults, factory methods).
     */
    @Target(ElementType.RECORD_COMPONENT)
    @Retention(RetentionPolicy.SOURCE)
    @interface Ignore {
    }
}
