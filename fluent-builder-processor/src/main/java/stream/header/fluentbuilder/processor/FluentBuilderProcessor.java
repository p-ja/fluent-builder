package stream.header.fluentbuilder.processor;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import stream.header.fluentbuilder.FluentBuilder;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@AutoService(Processor.class)
@SupportedAnnotationTypes("stream.header.fluentbuilder.FluentBuilder")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class FluentBuilderProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(FluentBuilder.class)) {
            if (element.getKind() != ElementKind.RECORD) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "@FluentBuilder can only be applied to records",
                        element
                );
                continue;
            }

            try {
                generateBuilder((TypeElement) element);
            } catch (IOException e) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "Failed to generate builder: " + e.getMessage(),
                        element
                );
            }
        }
        return true;
    }

    private void generateBuilder(TypeElement recordElement) throws IOException {
        String packageName = processingEnv.getElementUtils().getPackageOf(recordElement).getQualifiedName().toString();
        String recordName = recordElement.getSimpleName().toString();
        String builderClassName = recordName + "Builder";

        List<RecordComponent> mandatoryFields = new ArrayList<>();
        List<RecordComponent> optionalFields = new ArrayList<>();
        List<RecordComponent> ignoredFields = new ArrayList<>();
        List<RecordComponent> allFieldsInOrder = new ArrayList<>();

        for (Element enclosedElement : recordElement.getEnclosedElements()) {
            if (enclosedElement.getKind() == ElementKind.RECORD_COMPONENT) {
                RecordComponentElement component = (RecordComponentElement) enclosedElement;
                RecordComponent rc = new RecordComponent(
                        component.getSimpleName().toString(),
                        TypeName.get(component.asType())
                );

                allFieldsInOrder.add(rc);

                // Check for ignored fields
                if (component.getAnnotation(FluentBuilder.Ignore.class) != null) {
                    ignoredFields.add(rc);
                    continue;
                }

                if (component.getAnnotation(FluentBuilder.Mandatory.class) != null) {
                    mandatoryFields.add(rc);
                } else {
                    optionalFields.add(rc);
                }
            }
        }

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(builderClassName)
                .addModifiers(Modifier.PUBLIC);

        // Generate static builder() method
        MethodSpec builderMethod = MethodSpec.methodBuilder("builder")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(ClassName.get(packageName, builderClassName, "InitialBuilder"))
                .addStatement("return new InitialBuilder()")
                .build();
        classBuilder.addMethod(builderMethod);

        // Generate OptionalBuilderGetters interface
        if (!optionalFields.isEmpty()) {
            TypeSpec gettersInterface = generateOptionalGettersInterface(optionalFields);
            classBuilder.addType(gettersInterface);

            // Generate OptionalBuilder abstract class
            TypeSpec optionalBuilder = generateOptionalBuilder(optionalFields);
            classBuilder.addType(optionalBuilder);
        }

        // Generate builder classes for each level
        int numMandatory = mandatoryFields.size();

        if (numMandatory == 0) {
            // No mandatory fields, just generate a simple builder with build() method
            TypeSpec finalBuilder = generateSimpleBuilder(recordElement, recordName, optionalFields, allFieldsInOrder, ignoredFields);
            classBuilder.addType(finalBuilder);
        } else {
            // Generate InitialBuilder (level 0)
            TypeSpec initialBuilder = generateInitialBuilder(mandatoryFields, optionalFields);
            classBuilder.addType(initialBuilder);

            // Generate intermediate builders (levels 1 to N-1)
            List<List<RecordComponent>> combinations = generateCombinations(mandatoryFields);
            for (List<RecordComponent> providedFields : combinations) {
                if (providedFields.size() > 0 && providedFields.size() < numMandatory) {
                    TypeSpec intermediateBuilder = generateIntermediateBuilder(
                            mandatoryFields, providedFields, optionalFields
                    );
                    classBuilder.addType(intermediateBuilder);
                }
            }

            // Generate FinalBuilder (level N)
            TypeSpec finalBuilder = generateFinalBuilder(recordElement, recordName, mandatoryFields, optionalFields, allFieldsInOrder, ignoredFields);
            classBuilder.addType(finalBuilder);
        }

        JavaFile javaFile = JavaFile.builder(packageName, classBuilder.build())
                .build();

        javaFile.writeTo(processingEnv.getFiler());
    }

    private TypeSpec generateOptionalGettersInterface(List<RecordComponent> optionalFields) {
        TypeSpec.Builder interfaceBuilder = TypeSpec.interfaceBuilder("OptionalBuilderGetters")
                .addModifiers(Modifier.STATIC);

        for (RecordComponent field : optionalFields) {
            MethodSpec getter = MethodSpec.methodBuilder(field.name)
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .returns(field.type)
                    .build();
            interfaceBuilder.addMethod(getter);
        }

        return interfaceBuilder.build();
    }

    private TypeSpec generateOptionalBuilder(List<RecordComponent> optionalFields) {
        TypeVariableName typeVar = TypeVariableName.get("B");
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder("OptionalBuilder")
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT, Modifier.STATIC)
                .addTypeVariable(typeVar)
                .addSuperinterface(ClassName.bestGuess("OptionalBuilderGetters"));

        // Add fields
        for (RecordComponent field : optionalFields) {
            classBuilder.addField(field.type, field.name, Modifier.PROTECTED);
        }

        // Add abstract thisBuilder() method
        MethodSpec thisBuilder = MethodSpec.methodBuilder("thisBuilder")
                .addModifiers(Modifier.PROTECTED, Modifier.ABSTRACT)
                .returns(typeVar)
                .build();
        classBuilder.addMethod(thisBuilder);

        // Add setter methods
        for (RecordComponent field : optionalFields) {
            MethodSpec setter = MethodSpec.methodBuilder(field.name)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(typeVar)
                    .addParameter(field.type, field.name)
                    .addStatement("this.$N = $N", field.name, field.name)
                    .addStatement("return thisBuilder()")
                    .build();
            classBuilder.addMethod(setter);
        }

        // Add getter implementations
        for (RecordComponent field : optionalFields) {
            MethodSpec getter = MethodSpec.methodBuilder(field.name)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Override.class)
                    .returns(field.type)
                    .addStatement("return $N", field.name)
                    .build();
            classBuilder.addMethod(getter);
        }

        return classBuilder.build();
    }

    private TypeSpec generateInitialBuilder(List<RecordComponent> mandatoryFields, List<RecordComponent> optionalFields) {
        ClassName initialBuilderClass = ClassName.bestGuess("InitialBuilder");
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder("InitialBuilder")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC);

        if (!optionalFields.isEmpty()) {
            classBuilder.superclass(ParameterizedTypeName.get(
                    ClassName.bestGuess("OptionalBuilder"),
                    initialBuilderClass
            ));

            // Override thisBuilder()
            MethodSpec thisBuilder = MethodSpec.methodBuilder("thisBuilder")
                    .addModifiers(Modifier.PROTECTED)
                    .addAnnotation(Override.class)
                    .returns(initialBuilderClass)
                    .addStatement("return this")
                    .build();
            classBuilder.addMethod(thisBuilder);
        }

        // Add methods for each mandatory field
        for (RecordComponent field : mandatoryFields) {
            List<RecordComponent> remaining = new ArrayList<>(mandatoryFields);
            remaining.remove(field);

            String nextBuilderName = remaining.isEmpty() ? "FinalBuilder" :
                    "Builder" + generateBuilderSuffix(remaining);

            MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(field.name)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(ClassName.bestGuess(nextBuilderName))
                    .addParameter(field.type, field.name);

            if (optionalFields.isEmpty()) {
                methodBuilder.addStatement("return new $N($N)", nextBuilderName, field.name);
            } else {
                methodBuilder.addStatement("return new $N($N, this)", nextBuilderName, field.name);
            }

            classBuilder.addMethod(methodBuilder.build());
        }

        return classBuilder.build();
    }

    private TypeSpec generateIntermediateBuilder(
            List<RecordComponent> allMandatory,
            List<RecordComponent> providedFields,
            List<RecordComponent> optionalFields) {

        List<RecordComponent> remaining = new ArrayList<>(allMandatory);
        remaining.removeAll(providedFields);

        String builderName = "Builder" + generateBuilderSuffix(remaining);
        ClassName builderClass = ClassName.bestGuess(builderName);

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(builderName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC);

        if (!optionalFields.isEmpty()) {
            classBuilder.superclass(ParameterizedTypeName.get(
                    ClassName.bestGuess("OptionalBuilder"),
                    builderClass
            ));
        }

        // Add fields for provided mandatory fields (in original order)
        List<RecordComponent> sortedProvidedFields = new ArrayList<>();
        for (RecordComponent mc : allMandatory) {
            if (providedFields.contains(mc)) {
                sortedProvidedFields.add(mc);
            }
        }

        for (RecordComponent field : sortedProvidedFields) {
            classBuilder.addField(field.type, field.name, Modifier.PRIVATE, Modifier.FINAL);
        }

        // Add constructor (with parameters in original order)
        MethodSpec.Builder constructorBuilder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC);

        for (RecordComponent field : sortedProvidedFields) {
            constructorBuilder.addParameter(field.type, field.name);
        }

        if (!optionalFields.isEmpty()) {
            constructorBuilder.addParameter(ClassName.bestGuess("OptionalBuilderGetters"), "parentBuilder");
            for (RecordComponent field : optionalFields) {
                constructorBuilder.addStatement("this.$N = parentBuilder.$N()", field.name, field.name);
            }
        }

        for (RecordComponent field : sortedProvidedFields) {
            constructorBuilder.addStatement("this.$N = $N", field.name, field.name);
        }

        classBuilder.addMethod(constructorBuilder.build());

        if (!optionalFields.isEmpty()) {
            // Override thisBuilder()
            MethodSpec thisBuilder = MethodSpec.methodBuilder("thisBuilder")
                    .addModifiers(Modifier.PROTECTED)
                    .addAnnotation(Override.class)
                    .returns(builderClass)
                    .addStatement("return this")
                    .build();
            classBuilder.addMethod(thisBuilder);
        }

        // Add methods for remaining mandatory fields
        for (RecordComponent field : remaining) {
            List<RecordComponent> nextProvided = new ArrayList<>(providedFields);
            nextProvided.add(field);

            List<RecordComponent> nextRemaining = new ArrayList<>(allMandatory);
            nextRemaining.removeAll(nextProvided);

            String nextBuilderName = nextRemaining.isEmpty() ? "FinalBuilder" :
                    "Builder" + generateBuilderSuffix(nextRemaining);

            MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(field.name)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(ClassName.bestGuess(nextBuilderName))
                    .addParameter(field.type, field.name);

            // Sort parameters according to original order in allMandatory
            List<RecordComponent> sortedParams = new ArrayList<>();
            for (RecordComponent mc : allMandatory) {
                if (nextProvided.contains(mc)) {
                    sortedParams.add(mc);
                }
            }

            StringBuilder params = new StringBuilder();
            for (RecordComponent pf : sortedParams) {
                if (params.length() > 0) params.append(", ");
                params.append(pf.name);
            }

            if (optionalFields.isEmpty()) {
                methodBuilder.addStatement("return new $N($N)", nextBuilderName, params.toString());
            } else {
                methodBuilder.addStatement("return new $N($N, this)", nextBuilderName, params.toString());
            }

            classBuilder.addMethod(methodBuilder.build());
        }

        return classBuilder.build();
    }

    private TypeSpec generateFinalBuilder(
            TypeElement recordElement,
            String recordName,
            List<RecordComponent> mandatoryFields,
            List<RecordComponent> optionalFields,
            List<RecordComponent> allFieldsInOrder,
            List<RecordComponent> ignoredFields) {

        ClassName finalBuilderClass = ClassName.bestGuess("FinalBuilder");
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder("FinalBuilder")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC);

        if (!optionalFields.isEmpty()) {
            classBuilder.superclass(ParameterizedTypeName.get(
                    ClassName.bestGuess("OptionalBuilder"),
                    finalBuilderClass
            ));
        }

        // Add fields for mandatory fields
        for (RecordComponent field : mandatoryFields) {
            classBuilder.addField(field.type, field.name, Modifier.PRIVATE, Modifier.FINAL);
        }

        // Add constructor
        MethodSpec.Builder constructorBuilder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC);

        for (RecordComponent field : mandatoryFields) {
            constructorBuilder.addParameter(field.type, field.name);
        }

        if (!optionalFields.isEmpty()) {
            constructorBuilder.addParameter(ClassName.bestGuess("OptionalBuilderGetters"), "parentBuilder");
            for (RecordComponent field : optionalFields) {
                constructorBuilder.addStatement("this.$N = parentBuilder.$N()", field.name, field.name);
            }
        }

        for (RecordComponent field : mandatoryFields) {
            constructorBuilder.addStatement("this.$N = $N", field.name, field.name);
        }

        classBuilder.addMethod(constructorBuilder.build());

        if (!optionalFields.isEmpty()) {
            // Override thisBuilder()
            MethodSpec thisBuilder = MethodSpec.methodBuilder("thisBuilder")
                    .addModifiers(Modifier.PROTECTED)
                    .addAnnotation(Override.class)
                    .returns(finalBuilderClass)
                    .addStatement("return this")
                    .build();
            classBuilder.addMethod(thisBuilder);
        }

        // Add build() method
        MethodSpec.Builder buildMethod = MethodSpec.methodBuilder("build")
                .addModifiers(Modifier.PUBLIC)
                .returns(ClassName.get(recordElement));

        // Build parameters in the original record field order
        StringBuilder params = new StringBuilder();
        for (RecordComponent field : allFieldsInOrder) {
            if (params.length() > 0) params.append(", ");

            if (ignoredFields.contains(field)) {
                // For ignored fields, pass appropriate default value
                params.append(getDefaultValue(field.type));
            } else {
                // For mandatory and optional fields, use the field value
                params.append(field.name);
            }
        }

        buildMethod.addStatement("return new $N($N)", recordName, params.toString());
        classBuilder.addMethod(buildMethod.build());

        return classBuilder.build();
    }

    private TypeSpec generateSimpleBuilder(
            TypeElement recordElement,
            String recordName,
            List<RecordComponent> optionalFields,
            List<RecordComponent> allFieldsInOrder,
            List<RecordComponent> ignoredFields) {

        ClassName initialBuilderClass = ClassName.bestGuess("InitialBuilder");
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder("InitialBuilder")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC);

        if (!optionalFields.isEmpty()) {
            classBuilder.superclass(ParameterizedTypeName.get(
                    ClassName.bestGuess("OptionalBuilder"),
                    initialBuilderClass
            ));

            // Override thisBuilder()
            MethodSpec thisBuilder = MethodSpec.methodBuilder("thisBuilder")
                    .addModifiers(Modifier.PROTECTED)
                    .addAnnotation(Override.class)
                    .returns(initialBuilderClass)
                    .addStatement("return this")
                    .build();
            classBuilder.addMethod(thisBuilder);
        }

        // Add build() method
        MethodSpec.Builder buildMethod = MethodSpec.methodBuilder("build")
                .addModifiers(Modifier.PUBLIC)
                .returns(ClassName.get(recordElement));

        if (allFieldsInOrder.isEmpty()) {
            buildMethod.addStatement("return new $N()", recordName);
        } else {
            // Build parameters in the original record field order
            StringBuilder params = new StringBuilder();
            for (RecordComponent field : allFieldsInOrder) {
                if (params.length() > 0) params.append(", ");

                if (ignoredFields.contains(field)) {
                    // For ignored fields, pass appropriate default value
                    params.append(getDefaultValue(field.type));
                } else {
                    // For optional fields, use the field value
                    params.append(field.name);
                }
            }
            buildMethod.addStatement("return new $N($N)", recordName, params.toString());
        }

        classBuilder.addMethod(buildMethod.build());

        return classBuilder.build();
    }

    private String generateBuilderSuffix(List<RecordComponent> remainingFields) {
        return remainingFields.stream()
                .map(f -> {
                    String name = f.name;
                    // Extract number from field name if it matches pattern like "m1", "m2", etc.
                    if (name.length() > 1 && name.charAt(0) == 'm') {
                        try {
                            return name.substring(1);
                        } catch (Exception e) {
                            return String.valueOf(name.hashCode());
                        }
                    }
                    return String.valueOf(name.hashCode() & 0xFFFF);
                })
                .collect(Collectors.joining());
    }

    private List<List<RecordComponent>> generateCombinations(List<RecordComponent> fields) {
        List<List<RecordComponent>> result = new ArrayList<>();
        int n = fields.size();

        // Generate all combinations of 1 to n-1 fields
        for (int size = 1; size < n; size++) {
            generateCombinationsOfSize(fields, size, 0, new ArrayList<>(), result);
        }

        return result;
    }

    private void generateCombinationsOfSize(
            List<RecordComponent> fields,
            int size,
            int start,
            List<RecordComponent> current,
            List<List<RecordComponent>> result) {

        if (current.size() == size) {
            result.add(new ArrayList<>(current));
            return;
        }

        for (int i = start; i < fields.size(); i++) {
            current.add(fields.get(i));
            generateCombinationsOfSize(fields, size, i + 1, current, result);
            current.remove(current.size() - 1);
        }
    }

    private String getDefaultValue(TypeName type) {
        // Return appropriate default value based on type
        if (type.isPrimitive()) {
            String typeName = type.toString();
            return switch (typeName) {
                case "int" -> "0";
                case "long" -> "0L";
                case "double" -> "0.0";
                case "float" -> "0.0f";
                case "boolean" -> "false";
                case "byte" -> "(byte) 0";
                case "short" -> "(short) 0";
                case "char" -> "'\\0'";
                default -> "null";
            };
        }
        return "null";
    }

    private static class RecordComponent {
        final String name;
        final TypeName type;

        RecordComponent(String name, TypeName type) {
            this.name = name;
            this.type = type;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RecordComponent that = (RecordComponent) o;
            return Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }
    }
}
