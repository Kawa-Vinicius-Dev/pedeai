package com.pedeai.catalog.service;

import com.pedeai.catalog.domain.OptionDraft;
import com.pedeai.catalog.domain.OptionGroup;
import com.pedeai.catalog.domain.PricingRule;
import com.pedeai.catalog.dto.OptionGroupRequest;
import com.pedeai.catalog.dto.OptionGroupResponse;
import com.pedeai.catalog.dto.OptionItemRequest;
import com.pedeai.catalog.repository.OptionGroupRepository;
import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.pedeai.support.TestSecurity.CLOCK;
import static com.pedeai.support.TestSecurity.NOW;
import static com.pedeai.support.TestSecurity.STORE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OptionGroupServiceTest {
    @Mock
    private OptionGroupRepository repository;

    private OptionGroupService service;

    @BeforeEach
    void setUp() {
        service = new OptionGroupService(repository, CLOCK);
    }

    @Test
    void createNormalizesNamesAndBlankCodes() {
        when(repository.save(any(OptionGroup.class))).then(returnsFirstArg());

        OptionGroupResponse created = service.create(STORE_ID, request(PricingRule.MAX, 1, 2,
                option(" Calabresa ", " 101 ", 4590, true), option("Atum", "  ", 4790, true)));

        assertThat(created.options()).extracting("name").containsExactly("Calabresa", "Atum");
        assertThat(created.options()).extracting("code").containsExactly("101", null);
    }

    @Test
    void rejectsMinimumAboveMaximum() {
        assertThatThrownBy(() -> service.create(STORE_ID, request(PricingRule.SUM, 3, 2, option("Bacon", null, 400, true))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(OptionGroupService.MIN_GREATER_THAN_MAX);
    }

    @Test
    void requiresAtLeastOneActiveOption() {
        assertThatThrownBy(() -> service.create(STORE_ID, request(PricingRule.SUM, 0, 1, option("Bacon", null, 400, false))))
                .hasMessage(OptionGroupService.NO_ACTIVE_OPTION);
    }

    @Test
    void flavorsMinimumMustFitTheActiveOptions() {
        assertThatThrownBy(() -> service.create(STORE_ID, request(PricingRule.MAX, 3, 3,
                option("Calabresa", null, 4590, true), option("Atum", null, 4790, true))))
                .hasMessage(OptionGroupService.NOT_ENOUGH_OPTIONS);
    }

    @Test
    void sumGroupsMayRequireMoreChoicesThanOptionsBecauseRepeatsAreAllowed() {
        when(repository.save(any(OptionGroup.class))).then(returnsFirstArg());

        OptionGroupResponse created = service.create(STORE_ID, request(PricingRule.SUM, 3, 5,
                option("Bola de sorvete", null, 600, true)));

        assertThat(created.minChoices()).isEqualTo(3);
    }

    @Test
    void rejectsRepeatedCodeInTheGroup() {
        assertThatThrownBy(() -> service.create(STORE_ID, request(PricingRule.SUM, 0, 2,
                option("Bacon", "7", 400, true), option("Cheddar", "7", 350, true))))
                .hasMessage("Código PDV repetido no grupo: 7.");
    }

    @Test
    void pausesASingleOption() {
        OptionGroup group = new OptionGroup(STORE_ID, "Sabores", 1, 2, PricingRule.MAX, true,
                List.of(new OptionDraft(null, null, "Calabresa", 4590, true, true)), NOW);
        UUID calabresa = group.getOptions().getFirst().getId();
        when(repository.findByIdAndStoreId(group.getId(), STORE_ID)).thenReturn(Optional.of(group));

        OptionGroupResponse updated = service.changeOptionAvailability(STORE_ID, group.getId(), calabresa, false);

        assertThat(updated.options().getFirst().available()).isFalse();
    }

    @Test
    void pausingAnUnknownOptionIsNotFound() {
        OptionGroup group = new OptionGroup(STORE_ID, "Sabores", 1, 2, PricingRule.MAX, true,
                List.of(new OptionDraft(null, null, "Calabresa", 4590, true, true)), NOW);
        when(repository.findByIdAndStoreId(group.getId(), STORE_ID)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> service.changeOptionAvailability(STORE_ID, group.getId(), UUID.randomUUID(), false))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(OptionGroupService.OPTION_NOT_FOUND);
    }

    private static OptionGroupRequest request(PricingRule rule, int min, int max, OptionItemRequest... options) {
        return new OptionGroupRequest("Grupo", min, max, rule, true, List.of(options));
    }

    private static OptionItemRequest option(String name, String code, long priceCents, boolean active) {
        return new OptionItemRequest(null, code, name, priceCents, true, active);
    }
}
