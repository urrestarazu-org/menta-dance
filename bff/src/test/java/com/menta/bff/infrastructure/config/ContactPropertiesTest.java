package com.menta.bff.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ContactProperties")
class ContactPropertiesTest {

    @Test
    @DisplayName("has no name by default and a blank name counts as none")
    void blankNameIsNotShown() {
        ContactProperties properties = new ContactProperties();
        assertThat(properties.hasName()).isFalse();

        properties.setName("   ");
        assertThat(properties.hasName()).isFalse();
    }

    @Test
    @DisplayName("a configured name is shown")
    void configuredNameIsShown() {
        ContactProperties properties = new ContactProperties();
        properties.setName("Profe Ejemplo");

        assertThat(properties.hasName()).isTrue();
    }

    @Test
    @DisplayName("a name alone is not a contact detail: the page still asks for configuration")
    void nameAloneDoesNotMakeThePageNonEmpty() {
        ContactProperties properties = new ContactProperties();
        properties.setName("Profe Ejemplo");

        assertThat(properties.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("the tel: link keeps the digits of a phone written with a label")
    void phoneHrefKeepsOnlyTheDigits() {
        ContactProperties properties = new ContactProperties();
        properties.setPhone("cel. 000-0000000");

        assertThat(properties.phoneHref()).isEqualTo("0000000000");
    }
}
