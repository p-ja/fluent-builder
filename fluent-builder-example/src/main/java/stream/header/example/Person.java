package stream.header.example;

import stream.header.fluentbuilder.FluentBuilder;
import stream.header.fluentbuilder.FluentBuilder.Ignore;
import stream.header.fluentbuilder.FluentBuilder.Mandatory;

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
