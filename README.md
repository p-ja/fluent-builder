# Fluent Builder Generator

A Java annotation processor library that automatically generates type-safe fluent builders for Java records with mandatory and optional fields.

## Features

- **Type-safe fluent builders**: Generated builders enforce that all mandatory fields must be set before `build()` can be called
- **Flexible field ordering**: Mandatory fields can be set in any order
- **Optional fields**: Non-mandatory fields can be set at any time during the building process
- **Ignored fields**: Fields marked with `@Ignore` are excluded from the builder (useful for computed/internal fields)
- **Compile-time code generation**: Uses annotation processing to generate builders at compile time
- **Works with any number of mandatory fields**: Automatically generates the appropriate builder levels

## Project Structure

```
fluent-builder-parent/
├── fluent-builder-annotations/    # Contains @FluentBuilder and other annotations
├── fluent-builder-processor/      # Annotation processor that generates builder code
└── fluent-builder-example/        # Example usage
```

## Usage

### 1. Add dependencies to your project

```xml

<dependencies>
  <dependency>
    <groupId>stream.header.fluentbuilder</groupId>
    <artifactId>fluent-builder-annotations</artifactId>
    <version>0.1.3</version>
  </dependency>
  <dependency>
    <groupId>stream.header.fluentbuilder</groupId>
    <artifactId>fluent-builder-processor</artifactId>
    <version>0.1.3</version>
    <scope>provided</scope>
  </dependency>
</dependencies>

<build>
<plugins>
  <plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.11.0</version>
    <configuration>
      <annotationProcessorPaths>
        <path>
          <groupId>stream.header.fluentbuilder</groupId>
          <artifactId>fluent-builder-processor</artifactId>
          <version>0.1.3</version>
        </path>
      </annotationProcessorPaths>
    </configuration>
  </plugin>
</plugins>
</build>
```

### 2. Annotate your record

```java
import stream.header.fluentbuilder.FluentBuilder;
import stream.header.fluentbuilder.FluentBuilder.Mandatory;
import stream.header.fluentbuilder.FluentBuilder.Ignore;

@FluentBuilder
public record Person(
  @Mandatory String firstName,
  @Mandatory String lastName,
  @Mandatory int age,
  String email,
  String phoneNumber,
  String address,
  @Ignore String internalId  // Ignored - not part of builder, will be null
) {
}
```

### 3. Use the generated builder

```java
// All mandatory fields must be set before build() is available
Person person1 = PersonBuilder.builder()
    .firstName("John")
    .lastName("Doe")
    .age(30)
    .email("john.doe@example.com")
    .phoneNumber("123-456-7890")
    .build();

// Mandatory fields can be set in any order
Person person2 = PersonBuilder.builder()
  .lastName("Smith")
  .email("jane.smith@example.com")  // Optional field can be set anytime
  .firstName("Jane")
  .address("123 Main St")
  .age(25)
  .build();

// Only mandatory fields
Person person3 = PersonBuilder.builder()
  .age(40)
  .firstName("Bob")
  .lastName("Johnson")
  .build();
```

## How It Works

The annotation processor generates a multi-level builder pattern:

1. **OptionalBuilder**: Abstract base class with methods for all optional fields
2. **InitialBuilder**: Starting point - offers methods for each mandatory field
3. **Intermediate Builders**: One for each combination of mandatory fields not yet set
4. **FinalBuilder**: Has all mandatory fields set - only this builder has a `build()` method

This ensures at compile time that you cannot call `build()` until all mandatory fields have been provided.

## Annotations

### @FluentBuilder

Place on a record to generate a fluent builder for it.

### @FluentBuilder.Mandatory

Mark record components that must be set before `build()` can be called. These fields enforce compile-time type safety.

### @FluentBuilder.Ignore

Mark record components to exclude them from the builder entirely. Ignored fields will be set to `null` when the record is constructed. Useful for:

- Internal/computed fields
- Fields set by factory methods
- Metadata fields not relevant during construction

## Example Generated Code

For the `Person` record above, the processor generates a `PersonBuilder` class with multiple nested builder classes. The type system ensures you can only call `build()` after setting all three mandatory fields (firstName, lastName, age).

## Building the Project

```bash
./mvnw clean verify
```

## Running the Example

```bash
./mvnw clean install
./mvnw exec:java -Dexec.mainClass="stream.header.example.Example" -pl fluent-builder-example
```
