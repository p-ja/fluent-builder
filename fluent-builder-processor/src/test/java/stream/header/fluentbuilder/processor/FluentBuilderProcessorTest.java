package stream.header.fluentbuilder.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static com.google.common.truth.Truth.assertThat;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

/**
 * Tests for FluentBuilderProcessor covering basic functionality and common scenarios.
 */
class FluentBuilderProcessorTest {

    @Test
    void testSimpleRecordWithOnlyMandatoryFields() {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "test.SimpleRecord",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;
                        import stream.header.fluentbuilder.FluentBuilder.Mandatory;

                        @FluentBuilder
                        public record SimpleRecord(
                            @Mandatory String name,
                            @Mandatory int age
                        ) {}
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("test.SimpleRecordBuilder");
    }

    @Test
    void testSimpleRecordWithOnlyOptionalFields() {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "test.OptionalRecord",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;

                        @FluentBuilder
                        public record OptionalRecord(
                            String name,
                            int age,
                            String email
                        ) {}
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("test.OptionalRecordBuilder");
    }

    @Test
    void testEmptyRecord() {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "test.EmptyRecord",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;

                        @FluentBuilder
                        public record EmptyRecord() {}
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("test.EmptyRecordBuilder");
    }

    @Test
    void testMixedMandatoryAndOptionalFields() {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "test.Person",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;
                        import stream.header.fluentbuilder.FluentBuilder.Mandatory;

                        @FluentBuilder
                        public record Person(
                            @Mandatory String firstName,
                            @Mandatory String lastName,
                            @Mandatory int age,
                            String email,
                            String phoneNumber,
                            String address
                        ) {}
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("test.PersonBuilder");

        // Verify the generated code contains expected methods
        assertThat(compilation)
                .generatedSourceFile("test.PersonBuilder")
                .contentsAsUtf8String()
                .contains("public static InitialBuilder builder()");

        assertThat(compilation)
                .generatedSourceFile("test.PersonBuilder")
                .contentsAsUtf8String()
                .contains("public Person build()");
    }

    @Test
    void testRecordWithIgnoredFields() throws Exception {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "test.RecordWithIgnored",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;
                        import stream.header.fluentbuilder.FluentBuilder.Mandatory;
                        import stream.header.fluentbuilder.FluentBuilder.Ignore;

                        @FluentBuilder
                        public record RecordWithIgnored(
                            @Mandatory String name,
                            String description,
                            @Ignore String internalId,
                            @Ignore long timestamp
                        ) {}
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("test.RecordWithIgnoredBuilder");

        // Verify ignored fields don't appear in builder methods
        JavaFileObject generatedFile = compilation
                .generatedSourceFile("test.RecordWithIgnoredBuilder")
                .orElseThrow();
        String generatedCode = generatedFile.getCharContent(false).toString();

        assertThat(generatedCode.contains("internalId(")).isFalse();
        assertThat(generatedCode.contains("timestamp(")).isFalse();
    }

    @Test
    void testRecordWithAllFieldsIgnored() throws Exception {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "test.AllIgnored",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;
                        import stream.header.fluentbuilder.FluentBuilder.Ignore;

                        @FluentBuilder
                        public record AllIgnored(
                            @Ignore String field1,
                            @Ignore int field2
                        ) {}
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("test.AllIgnoredBuilder");

        // Should have a simple builder with just build() method
        JavaFileObject generatedFile = compilation
                .generatedSourceFile("test.AllIgnoredBuilder")
                .orElseThrow();
        String generatedCode = generatedFile.getCharContent(false).toString();

        assertThat(generatedCode.contains("public AllIgnored build()")).isTrue();
    }

    @Test
    void testRecordWithPrimitiveTypes() {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "test.PrimitiveRecord",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;
                        import stream.header.fluentbuilder.FluentBuilder.Mandatory;

                        @FluentBuilder
                        public record PrimitiveRecord(
                            @Mandatory int intValue,
                            @Mandatory long longValue,
                            @Mandatory double doubleValue,
                            @Mandatory boolean boolValue,
                            @Mandatory byte byteValue,
                            @Mandatory short shortValue,
                            @Mandatory float floatValue,
                            @Mandatory char charValue
                        ) {}
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("test.PrimitiveRecordBuilder");
    }

    @Test
    void testRecordWithGenericTypes() {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "test.GenericRecord",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;
                        import stream.header.fluentbuilder.FluentBuilder.Mandatory;
                        import java.util.List;
                        import java.util.Map;
                        import java.util.Set;

                        @FluentBuilder
                        public record GenericRecord(
                            @Mandatory List<String> names,
                            @Mandatory Map<String, Integer> scores,
                            Set<Long> ids
                        ) {}
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("test.GenericRecordBuilder");
    }

    @Test
    void testRecordWithNestedGenerics() {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "test.NestedGenericRecord",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;
                        import stream.header.fluentbuilder.FluentBuilder.Mandatory;
                        import java.util.List;
                        import java.util.Map;

                        @FluentBuilder
                        public record NestedGenericRecord(
                            @Mandatory List<Map<String, List<Integer>>> complexData,
                            Map<String, List<String>> tags
                        ) {}
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("test.NestedGenericRecordBuilder");
    }

    @Test
    void testRecordWithArrayTypes() {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "test.ArrayRecord",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;
                        import stream.header.fluentbuilder.FluentBuilder.Mandatory;

                        @FluentBuilder
                        public record ArrayRecord(
                            @Mandatory String[] names,
                            @Mandatory int[] scores,
                            byte[][] matrix
                        ) {}
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("test.ArrayRecordBuilder");
    }

    @Test
    void testRecordWithMultipleMandatoryFields() throws Exception {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "test.MultipleMandatory",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;
                        import stream.header.fluentbuilder.FluentBuilder.Mandatory;

                        @FluentBuilder
                        public record MultipleMandatory(
                            @Mandatory String field1,
                            @Mandatory String field2,
                            @Mandatory String field3,
                            @Mandatory String field4
                        ) {}
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("test.MultipleMandatoryBuilder");

        // Verify intermediate builders are generated for different orderings
        JavaFileObject generatedFile = compilation
                .generatedSourceFile("test.MultipleMandatoryBuilder")
                .orElseThrow();
        String generatedCode = generatedFile.getCharContent(false).toString();

        assertThat(generatedCode.contains("class InitialBuilder")).isTrue();
        assertThat(generatedCode.contains("class FinalBuilder")).isTrue();
    }

    @Test
    void testRecordInDifferentPackage() {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "com.example.deep.nested.MyRecord",
                """
                        package com.example.deep.nested;

                        import stream.header.fluentbuilder.FluentBuilder;
                        import stream.header.fluentbuilder.FluentBuilder.Mandatory;

                        @FluentBuilder
                        public record MyRecord(
                            @Mandatory String value
                        ) {}
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("com.example.deep.nested.MyRecordBuilder");
    }

    @Test
    void testRecordWithCustomClassType() {
        JavaFileObject addressSource = JavaFileObjects.forSourceString(
                "test.Address",
                """
                        package test;

                        public record Address(String street, String city) {}
                        """
        );

        JavaFileObject personSource = JavaFileObjects.forSourceString(
                "test.Person",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;
                        import stream.header.fluentbuilder.FluentBuilder.Mandatory;

                        @FluentBuilder
                        public record Person(
                            @Mandatory String name,
                            Address address
                        ) {}
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(addressSource, personSource);

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("test.PersonBuilder");
    }

    @Test
    void testGeneratedBuilderCompilability() {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "test.User",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;
                        import stream.header.fluentbuilder.FluentBuilder.Mandatory;

                        @FluentBuilder
                        public record User(
                            @Mandatory String username,
                            @Mandatory String email,
                            String firstName,
                            String lastName
                        ) {}
                        """
        );

        JavaFileObject usage = JavaFileObjects.forSourceString(
                "test.UserUsage",
                """
                        package test;

                        public class UserUsage {
                            public static User createUser() {
                                return UserBuilder.builder()
                                    .username("john_doe")
                                    .email("john@example.com")
                                    .firstName("John")
                                    .lastName("Doe")
                                    .build();
                            }

                            public static User createUserDifferentOrder() {
                                return UserBuilder.builder()
                                    .email("jane@example.com")
                                    .firstName("Jane")
                                    .username("jane_doe")
                                    .build();
                            }
                        }
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(source, usage);

        assertThat(compilation).succeeded();
    }

    @Test
    void testOptionalBuilderGettersInterface() throws Exception {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "test.RecordWithOptionals",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;
                        import stream.header.fluentbuilder.FluentBuilder.Mandatory;

                        @FluentBuilder
                        public record RecordWithOptionals(
                            @Mandatory String required,
                            String optional1,
                            String optional2
                        ) {}
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(source);

        assertThat(compilation).succeeded();

        JavaFileObject generatedFile = compilation
                .generatedSourceFile("test.RecordWithOptionalsBuilder")
                .orElseThrow();
        String generatedCode = generatedFile.getCharContent(false).toString();

        // Verify OptionalBuilderGetters interface is generated
        assertThat(generatedCode.contains("interface OptionalBuilderGetters")).isTrue();
        assertThat(generatedCode.contains("abstract static class OptionalBuilder")).isTrue();
    }

    @Test
    void testRecordWithWrapperTypes() {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "test.WrapperRecord",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;
                        import stream.header.fluentbuilder.FluentBuilder.Mandatory;

                        @FluentBuilder
                        public record WrapperRecord(
                            @Mandatory Integer count,
                            @Mandatory Long id,
                            @Mandatory Double price,
                            @Mandatory Boolean active,
                            Character initial
                        ) {}
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("test.WrapperRecordBuilder");
    }
}
