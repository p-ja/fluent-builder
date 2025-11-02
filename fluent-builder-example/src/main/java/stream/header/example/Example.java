package stream.header.example;

/**
 * Example class demonstrating the usage of the PersonBuilder with different field configurations.
 * Shows how mandatory and optional fields can be set in various orders.
 */
public class Example {
    /**
     * Main method demonstrating various PersonBuilder usage patterns.
     *
     * @param args command line arguments (not used)
     */
    public static void main(String[] args) {
        // Example 1: All mandatory fields set in order
        Person person1 = PersonBuilder.builder()
                .firstName("John")
                .lastName("Doe")
                .age(30)
                .email("john.doe@example.com")
                .phoneNumber("123-456-7890")
                .build();

        System.out.println("Person 1: " + person1);

        // Example 2: Mandatory fields in different order with optional fields interspersed
        Person person2 = PersonBuilder.builder()
                .lastName("Smith")
                .email("jane.smith@example.com")
                .firstName("Jane")
                .address("123 Main St")
                .age(25)
                .build();

        System.out.println("Person 2: " + person2);

        // Example 3: Only mandatory fields
        Person person3 = PersonBuilder.builder()
                .age(40)
                .firstName("Bob")
                .lastName("Johnson")
                .build();

        System.out.println("Person 3: " + person3);
    }
}
