package stream.header.fluentbuilder.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static com.google.common.truth.Truth.assertThat;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

/**
 * Tests for validation and error cases in FluentBuilderProcessor.
 */
class ValidationTest {

    @Test
    void testAnnotationOnClassShouldFail() {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "test.NotARecord",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;

                        @FluentBuilder
                        public class NotARecord {
                            private String name;
                            private int age;
                        }
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("@FluentBuilder can only be applied to records");
    }

    @Test
    void testAnnotationOnInterfaceShouldFail() {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "test.NotARecord",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;

                        @FluentBuilder
                        public interface NotARecord {
                            String getName();
                            int getAge();
                        }
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("@FluentBuilder can only be applied to records");
    }

    @Test
    void testAnnotationOnEnumShouldFail() {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "test.NotARecord",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;

                        @FluentBuilder
                        public enum NotARecord {
                            VALUE1, VALUE2
                        }
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("@FluentBuilder can only be applied to records");
    }

    @Test
    void testAnnotationOnAnnotationShouldFail() {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "test.NotARecord",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;
                        import java.lang.annotation.Retention;
                        import java.lang.annotation.RetentionPolicy;

                        @FluentBuilder
                        @Retention(RetentionPolicy.RUNTIME)
                        public @interface NotARecord {
                        }
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("@FluentBuilder can only be applied to records");
    }

    @Test
    void testRecordWithoutAnnotationShouldNotGenerateBuilder() {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "test.NoAnnotation",
                """
                        package test;

                        public record NoAnnotation(String name, int age) {}
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertThat(compilation.generatedSourceFile("test.NoAnnotationBuilder").isEmpty()).isTrue();
    }

    @Test
    void testMandatoryAnnotationOnNonRecordComponent() {
        // Java's compiler will reject @Mandatory on methods since it's @Target(RECORD_COMPONENT)
        // This is a compile-time check, not a processor check
        JavaFileObject source = JavaFileObjects.forSourceString(
                "test.TestRecord",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;
                        import stream.header.fluentbuilder.FluentBuilder.Mandatory;

                        @FluentBuilder
                        public record TestRecord(String name) {
                            // Method with Mandatory annotation - Java compiler rejects this
                            @Mandatory
                            public String getName() {
                                return name;
                            }
                        }
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(source);

        // Expected to fail at compile time due to annotation target mismatch
        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("not applicable");
    }

    @Test
    void testIgnoreAnnotationOnNonRecordComponent() {
        // Java's compiler will reject @Ignore on methods since it's @Target(RECORD_COMPONENT)
        // This is a compile-time check, not a processor check
        JavaFileObject source = JavaFileObjects.forSourceString(
                "test.TestRecord",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;
                        import stream.header.fluentbuilder.FluentBuilder.Ignore;

                        @FluentBuilder
                        public record TestRecord(String name) {
                            // Method with Ignore annotation - Java compiler rejects this
                            @Ignore
                            public String getName() {
                                return name;
                            }
                        }
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(source);

        // Expected to fail at compile time due to annotation target mismatch
        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("not applicable");
    }

    @Test
    void testRecordWithBothMandatoryAndIgnoreOnSameField() throws Exception {
        // Testing edge case - Mandatory takes precedence, but field is also ignored
        // This should succeed, with Ignore taking precedence (field should not be in builder)
        JavaFileObject source = JavaFileObjects.forSourceString(
                "test.ConflictingAnnotations",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;
                        import stream.header.fluentbuilder.FluentBuilder.Mandatory;
                        import stream.header.fluentbuilder.FluentBuilder.Ignore;

                        @FluentBuilder
                        public record ConflictingAnnotations(
                            @Mandatory @Ignore String name,
                            String description
                        ) {}
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(source);

        assertThat(compilation).succeeded();

        JavaFileObject generatedFile = compilation
                .generatedSourceFile("test.ConflictingAnnotationsBuilder")
                .orElseThrow();
        String generatedCode = generatedFile.getCharContent(false).toString();

        // The field should be ignored (Ignore takes precedence in the current implementation)
        assertThat(generatedCode.contains("name(")).isFalse();
    }

    @Test
    void testCorrectErrorMessageLocation() {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "test.WrongType",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;

                        @FluentBuilder
                        public class WrongType {
                            private String field;
                        }
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorCount(1);
        assertThat(compilation).hadErrorContaining("@FluentBuilder can only be applied to records");
    }

    @Test
    void testMultipleRecordsInSameCompilation() {
        JavaFileObject record1 = JavaFileObjects.forSourceString(
                "test.Record1",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;

                        @FluentBuilder
                        public record Record1(String field1) {}
                        """
        );

        JavaFileObject record2 = JavaFileObjects.forSourceString(
                "test.Record2",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;

                        @FluentBuilder
                        public record Record2(String field2) {}
                        """
        );

        JavaFileObject notRecord = JavaFileObjects.forSourceString(
                "test.NotRecord",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;

                        @FluentBuilder
                        public class NotRecord {
                            private String field;
                        }
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(record1, record2, notRecord);

        // The processor generates code for valid records, but the overall compilation fails
        // because of the error on NotRecord
        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("@FluentBuilder can only be applied to records");
        // Note: compile-testing doesn't let us check generated files when compilation fails
    }

    @Test
    void testNestedRecordWithAnnotation() {
        // Note: Nested records currently have a known limitation where the generated
        // builder needs to use the qualified name. This test documents the current behavior.
        JavaFileObject source = JavaFileObjects.forSourceString(
                "test.Outer",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;

                        public class Outer {
                            @FluentBuilder
                            public record Inner(String value) {}
                        }
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(source);

        // Currently fails because generated code doesn't use qualified name
        // This is a known limitation that could be fixed in a future version
        assertThat(compilation).failed();
    }

    @Test
    void testLocalRecordShouldWork() {
        // Local records (Java 16+) with annotation should work
        JavaFileObject source = JavaFileObjects.forSourceString(
                "test.Container",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;

                        public class Container {
                            public void method() {
                                @FluentBuilder
                                record LocalRecord(String value) {}
                            }
                        }
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(source);

        // Note: Local records are typically not accessible from outside,
        // but the processor should still handle them without error
        assertThat(compilation).succeeded();
    }

    @Test
    void testRecordWithVarargs() {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "test.VarargsRecord",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;
                        import stream.header.fluentbuilder.FluentBuilder.Mandatory;

                        @FluentBuilder
                        public record VarargsRecord(
                            @Mandatory String name,
                            String... values
                        ) {}
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(source);

        // Varargs in records are represented as arrays, should work fine
        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("test.VarargsRecordBuilder");
    }
}
