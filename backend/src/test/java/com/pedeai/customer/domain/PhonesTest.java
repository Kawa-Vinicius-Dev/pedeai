package com.pedeai.customer.domain;

import com.pedeai.shared.exception.BusinessRuleException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhonesTest {
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "(11) 99999-0000     | +5511999990000",
            "11999990000         | +5511999990000",
            "+55 11 99999-0000   | +5511999990000",
            "5511999990000       | +5511999990000",
            "(11) 3333-4444      | +551133334444",
            "+1 415 555 0100     | +14155550100"
    })
    void normalizesToE164(String typed, String expected) {
        assertThat(Phones.normalize(typed)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "99999-0000", "123", "+55 11 99999-0000-12345"})
    void rejectsWhatIsNotAPhone(String typed) {
        assertThatThrownBy(() -> Phones.normalize(typed))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(Phones.INVALID);
    }
}
