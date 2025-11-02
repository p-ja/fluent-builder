package stream.header.example;

import stream.header.fluentbuilder.FluentBuilder;
import stream.header.fluentbuilder.FluentBuilder.Ignore;
import stream.header.fluentbuilder.FluentBuilder.Mandatory;

/**
 * Person record with mandatory and optional fields.
 * Demonstrates the FluentBuilder annotation for generating a type-safe builder.
 *
 * @param firstName person's first name (mandatory)
 * @param lastName person's last name (mandatory)
 * @param age person's age (mandatory)
 * @param email person's email address (optional)
 * @param phoneNumber person's phone number (optional)
 * @param address person's address (optional)
 * @param internalId internal identifier (ignored by builder)
 */
@FluentBuilder
public record Person(
        @Mandatory String firstName,
        @Mandatory String lastName,
        @Mandatory int age,
        String email,
        String phoneNumber,
        String address,
        @Ignore String internalId  // Ignored field - not part of builder
) {
}
