package app.trashbin.tag.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TagNameTest {

    @Test
    void cleansWhitespaceAndControlCharactersAndNormalizesCase() {
        TagName name = TagName.parse("  Tax\tReturns \n 2019 ").orElseThrow();

        assertThat(name.display()).isEqualTo("Tax Returns 2019");
        assertThat(name.normalized()).isEqualTo("tax returns 2019");
    }

    @Test
    void rejectsBlankNullAndOverlongNames() {
        assertThat(TagName.parse(null)).isEmpty();
        assertThat(TagName.parse(" \t ")).isEmpty();
        assertThat(TagName.parse("x".repeat(51))).isEmpty();
        assertThat(TagName.parse("x".repeat(50))).isPresent();
    }
}
