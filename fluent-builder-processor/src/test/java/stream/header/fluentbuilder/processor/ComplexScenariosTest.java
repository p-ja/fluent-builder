package stream.header.fluentbuilder.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static com.google.common.truth.Truth.assertThat;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

/**
 * Tests for complex scenarios and edge cases in FluentBuilderProcessor.
 */
class ComplexScenariosTest {

    @Test
    void testManyMandatoryFieldsGeneratesIntermediateBuilders() throws Exception {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "test.ManyFields",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;
                        import stream.header.fluentbuilder.FluentBuilder.Mandatory;

                        @FluentBuilder
                        public record ManyFields(
                            @Mandatory String m1,
                            @Mandatory String m2,
                            @Mandatory String m3,
                            @Mandatory String m4,
                            @Mandatory String m5
                        ) {}
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(source);

        assertThat(compilation).succeeded();

        JavaFileObject generatedFile = compilation
                .generatedSourceFile("test.ManyFieldsBuilder")
                .orElseThrow();
        String generatedCode = generatedFile.getCharContent(false).toString();

        // Should have InitialBuilder, multiple intermediate builders, and FinalBuilder
        assertThat(generatedCode.contains("class InitialBuilder")).isTrue();
        assertThat(generatedCode.contains("class FinalBuilder")).isTrue();

        // Should generate intermediate builders for combinations
        assertThat(generatedCode.contains("class Builder")).isTrue();
    }

    @Test
    void testDifferentMandatoryFieldOrders() {
        JavaFileObject recordSource = JavaFileObjects.forSourceString(
                "test.OrderTest",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;
                        import stream.header.fluentbuilder.FluentBuilder.Mandatory;

                        @FluentBuilder
                        public record OrderTest(
                            @Mandatory String first,
                            @Mandatory String second,
                            @Mandatory String third
                        ) {}
                        """
        );

        JavaFileObject usageSource = JavaFileObjects.forSourceString(
                "test.OrderTestUsage",
                """
                        package test;

                        public class OrderTestUsage {
                            public static void testOrders() {
                                // Order 1: first, second, third
                                OrderTest t1 = OrderTestBuilder.builder()
                                    .first("a")
                                    .second("b")
                                    .third("c")
                                    .build();

                                // Order 2: third, first, second
                                OrderTest t2 = OrderTestBuilder.builder()
                                    .third("c")
                                    .first("a")
                                    .second("b")
                                    .build();

                                // Order 3: second, third, first
                                OrderTest t3 = OrderTestBuilder.builder()
                                    .second("b")
                                    .third("c")
                                    .first("a")
                                    .build();
                            }
                        }
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(recordSource, usageSource);

        assertThat(compilation).succeeded();
    }

    @Test
    void testOptionalFieldsCanBeSetAtAnyStage() {
        JavaFileObject recordSource = JavaFileObjects.forSourceString(
                "test.FlexibleOptionals",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;
                        import stream.header.fluentbuilder.FluentBuilder.Mandatory;

                        @FluentBuilder
                        public record FlexibleOptionals(
                            @Mandatory String required1,
                            @Mandatory String required2,
                            String optional1,
                            String optional2
                        ) {}
                        """
        );

        JavaFileObject usageSource = JavaFileObjects.forSourceString(
                "test.FlexibleOptionalsUsage",
                """
                        package test;

                        public class FlexibleOptionalsUsage {
                            public static void test() {
                                // Set optionals at different stages
                                FlexibleOptionals obj1 = FlexibleOptionalsBuilder.builder()
                                    .optional1("opt1")  // Before any mandatory
                                    .required1("req1")
                                    .optional2("opt2")  // Between mandatories
                                    .required2("req2")
                                    .build();

                                // Set optionals after all mandatories
                                FlexibleOptionals obj2 = FlexibleOptionalsBuilder.builder()
                                    .required1("req1")
                                    .required2("req2")
                                    .optional1("opt1")
                                    .optional2("opt2")
                                    .build();

                                // Don't set any optionals
                                FlexibleOptionals obj3 = FlexibleOptionalsBuilder.builder()
                                    .required1("req1")
                                    .required2("req2")
                                    .build();
                            }
                        }
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(recordSource, usageSource);

        assertThat(compilation).succeeded();
    }

    @Test
    void testRecordWithGenericTypeParameters() {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "test.GenericContainer",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;
                        import stream.header.fluentbuilder.FluentBuilder.Mandatory;
                        import java.util.List;
                        import java.util.Optional;
                        import java.util.function.Supplier;

                        @FluentBuilder
                        public record GenericContainer(
                            @Mandatory List<String> items,
                            @Mandatory Optional<Integer> count,
                            Supplier<String> defaultValue
                        ) {}
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("test.GenericContainerBuilder");
    }

    @Test
    void testRecordWithWildcardGenerics() {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "test.WildcardRecord",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;
                        import stream.header.fluentbuilder.FluentBuilder.Mandatory;
                        import java.util.List;

                        @FluentBuilder
                        public record WildcardRecord(
                            @Mandatory List<?> wildcardList,
                            List<? extends Number> boundedList
                        ) {}
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("test.WildcardRecordBuilder");
    }

    @Test
    void testRecordReferencingAnotherAnnotatedRecord() {
        JavaFileObject address = JavaFileObjects.forSourceString(
                "test.Address",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;
                        import stream.header.fluentbuilder.FluentBuilder.Mandatory;

                        @FluentBuilder
                        public record Address(
                            @Mandatory String street,
                            @Mandatory String city,
                            String state
                        ) {}
                        """
        );

        JavaFileObject person = JavaFileObjects.forSourceString(
                "test.Person",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;
                        import stream.header.fluentbuilder.FluentBuilder.Mandatory;

                        @FluentBuilder
                        public record Person(
                            @Mandatory String name,
                            @Mandatory Address address
                        ) {}
                        """
        );

        JavaFileObject usage = JavaFileObjects.forSourceString(
                "test.PersonUsage",
                """
                        package test;

                        public class PersonUsage {
                            public static void test() {
                                Address addr = AddressBuilder.builder()
                                    .street("123 Main St")
                                    .city("Springfield")
                                    .build();

                                Person person = PersonBuilder.builder()
                                    .name("John Doe")
                                    .address(addr)
                                    .build();
                            }
                        }
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(address, person, usage);

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("test.AddressBuilder");
        assertThat(compilation).generatedSourceFile("test.PersonBuilder");
    }

    @Test
    void testRecordWithManyOptionalFields() throws Exception {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "test.ManyOptionals",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;
                        import stream.header.fluentbuilder.FluentBuilder.Mandatory;

                        @FluentBuilder
                        public record ManyOptionals(
                            @Mandatory String id,
                            String opt1,
                            String opt2,
                            String opt3,
                            String opt4,
                            String opt5,
                            String opt6,
                            String opt7,
                            String opt8,
                            String opt9,
                            String opt10
                        ) {}
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(source);

        assertThat(compilation).succeeded();

        JavaFileObject generatedFile = compilation
                .generatedSourceFile("test.ManyOptionalsBuilder")
                .orElseThrow();
        String generatedCode = generatedFile.getCharContent(false).toString();

        // Should have OptionalBuilder with all optional fields
        assertThat(generatedCode.contains("abstract static class OptionalBuilder")).isTrue();
        assertThat(generatedCode.contains("opt1")).isTrue();
        assertThat(generatedCode.contains("opt10")).isTrue();
    }

    @Test
    void testComplexRealWorldExample() {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "test.Product",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;
                        import stream.header.fluentbuilder.FluentBuilder.Mandatory;
                        import stream.header.fluentbuilder.FluentBuilder.Ignore;
                        import java.util.List;
                        import java.util.Map;
                        import java.time.Instant;

                        @FluentBuilder
                        public record Product(
                            @Mandatory String id,
                            @Mandatory String name,
                            @Mandatory double price,
                            String description,
                            List<String> tags,
                            Map<String, String> metadata,
                            int stockQuantity,
                            boolean available,
                            @Ignore Instant createdAt,
                            @Ignore String internalCode
                        ) {}
                        """
        );

        JavaFileObject usage = JavaFileObjects.forSourceString(
                "test.ProductUsage",
                """
                        package test;

                        import java.util.List;
                        import java.util.Map;

                        public class ProductUsage {
                            public static void test() {
                                Product product = ProductBuilder.builder()
                                    .id("PROD-001")
                                    .name("Widget")
                                    .price(29.99)
                                    .description("A useful widget")
                                    .tags(List.of("electronics", "gadget"))
                                    .metadata(Map.of("color", "blue", "size", "medium"))
                                    .stockQuantity(100)
                                    .available(true)
                                    .build();

                                // Different order
                                Product product2 = ProductBuilder.builder()
                                    .price(49.99)
                                    .available(false)
                                    .name("Deluxe Widget")
                                    .id("PROD-002")
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
    void testRecordWithLongFieldNames() {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "test.LongNames",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;
                        import stream.header.fluentbuilder.FluentBuilder.Mandatory;

                        @FluentBuilder
                        public record LongNames(
                            @Mandatory String veryLongFieldNameThatDescribesTheFieldPurposeInDetail,
                            @Mandatory String anotherExtremelyLongFieldNameForTestingPurposes,
                            String thisIsAnOptionalFieldWithAVeryLongDescriptiveName
                        ) {}
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("test.LongNamesBuilder");
    }

    @Test
    void testRecordWithUnderscoresAndSpecialCharactersInNames() {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "test.SpecialNames",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;
                        import stream.header.fluentbuilder.FluentBuilder.Mandatory;

                        @FluentBuilder
                        public record SpecialNames(
                            @Mandatory String field_with_underscores,
                            @Mandatory String $fieldWithDollar,
                            String _fieldStartingWithUnderscore
                        ) {}
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("test.SpecialNamesBuilder");
    }

    @Test
    void testRecordWithSingleCharacterFieldNames() {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "test.ShortNames",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;
                        import stream.header.fluentbuilder.FluentBuilder.Mandatory;

                        @FluentBuilder
                        public record ShortNames(
                            @Mandatory String a,
                            @Mandatory String b,
                            String c,
                            int x,
                            int y,
                            int z
                        ) {}
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("test.ShortNamesBuilder");
    }

    @Test
    void testRecordWithOnlyOneMandatoryField() throws Exception {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "test.SingleMandatory",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;
                        import stream.header.fluentbuilder.FluentBuilder.Mandatory;

                        @FluentBuilder
                        public record SingleMandatory(
                            @Mandatory String required,
                            String opt1,
                            String opt2,
                            String opt3
                        ) {}
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(source);

        assertThat(compilation).succeeded();

        JavaFileObject generatedFile = compilation
                .generatedSourceFile("test.SingleMandatoryBuilder")
                .orElseThrow();
        String generatedCode = generatedFile.getCharContent(false).toString();

        // With only one mandatory field, should go directly from InitialBuilder to FinalBuilder
        assertThat(generatedCode.contains("class InitialBuilder")).isTrue();
        assertThat(generatedCode.contains("class FinalBuilder")).isTrue();
    }

    @Test
    void testRecordImplementingInterfaces() {
        JavaFileObject interfaceSource = JavaFileObjects.forSourceString(
                "test.Named",
                """
                        package test;

                        public interface Named {
                            String name();
                        }
                        """
        );

        JavaFileObject recordSource = JavaFileObjects.forSourceString(
                "test.NamedRecord",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;
                        import stream.header.fluentbuilder.FluentBuilder.Mandatory;

                        @FluentBuilder
                        public record NamedRecord(
                            @Mandatory String name,
                            String description
                        ) implements Named {}
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(interfaceSource, recordSource);

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("test.NamedRecordBuilder");
    }

    @Test
    void testRecordWithMultiDimensionalArrays() {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "test.Matrix",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;
                        import stream.header.fluentbuilder.FluentBuilder.Mandatory;

                        @FluentBuilder
                        public record Matrix(
                            @Mandatory int[][] matrix2D,
                            @Mandatory int[][][] matrix3D,
                            String[] tags
                        ) {}
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("test.MatrixBuilder");
    }

    @Test
    void testBuildMethodParameterOrderMatchesRecordDeclaration() throws Exception {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "test.OrderedRecord",
                """
                        package test;

                        import stream.header.fluentbuilder.FluentBuilder;
                        import stream.header.fluentbuilder.FluentBuilder.Mandatory;
                        import stream.header.fluentbuilder.FluentBuilder.Ignore;

                        @FluentBuilder
                        public record OrderedRecord(
                            String first,
                            @Mandatory String second,
                            @Ignore String third,
                            @Mandatory String fourth,
                            String fifth
                        ) {}
                        """
        );

        Compilation compilation = javac()
                .withProcessors(new FluentBuilderProcessor())
                .compile(source);

        assertThat(compilation).succeeded();

        JavaFileObject generatedFile = compilation
                .generatedSourceFile("test.OrderedRecordBuilder")
                .orElseThrow();
        String generatedCode = generatedFile.getCharContent(false).toString();

        // The build() method should create the record with parameters in declaration order
        // first, second, null, fourth, fifth
        assertThat(generatedCode.contains("public OrderedRecord build()")).isTrue();
        assertThat(generatedCode.contains("return new OrderedRecord(first, second, null, fourth, fifth)")).isTrue();
    }
}
