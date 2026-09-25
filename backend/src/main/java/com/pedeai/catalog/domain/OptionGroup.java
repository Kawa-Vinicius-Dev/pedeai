package com.pedeai.catalog.domain;

import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.id.UuidV7;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Grupo de adicionais com as suas opções: "Adicionais", "Sabores", "Ponto da carne". */
@Entity
@Table(name = "option_group")
public class OptionGroup {
    @Id
    private UUID id;
    private UUID storeId;
    private String name;
    private int minChoices;
    private int maxChoices;
    @Enumerated(EnumType.STRING)
    private PricingRule pricingRule;
    private boolean active;
    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "option_group_id", nullable = false)
    @OrderBy("sortOrder ASC")
    @BatchSize(size = 50)
    private List<OptionItem> options = new ArrayList<>();
    private Instant createdAt;
    private Instant updatedAt;
    @Version
    private Long version;

    protected OptionGroup() {
    }

    public OptionGroup(UUID storeId, String name, int minChoices, int maxChoices, PricingRule pricingRule,
                       boolean active, List<OptionDraft> options, Instant now) {
        this.id = UuidV7.generate();
        this.storeId = storeId;
        this.createdAt = now;
        update(name, minChoices, maxChoices, pricingRule, active, options, now);
    }

    /**
     * Substitui os dados do grupo. Opções com id são atualizadas, sem id são criadas, e as que não vieram
     * são retiradas (ficam inativas, nunca apagadas, porque pedidos antigos apontam para elas).
     */
    public void update(String name, int minChoices, int maxChoices, PricingRule pricingRule, boolean active,
                       List<OptionDraft> drafts, Instant now) {
        this.name = name;
        this.minChoices = minChoices;
        this.maxChoices = maxChoices;
        this.pricingRule = pricingRule;
        this.active = active;
        this.updatedAt = now;

        Map<UUID, OptionItem> existing = options.stream()
                .collect(Collectors.toMap(OptionItem::getId, Function.identity()));
        Set<UUID> kept = new HashSet<>();
        int position = 0;
        for (OptionDraft draft : drafts) {
            if (draft.id() == null) {
                options.add(new OptionItem(storeId, draft, position++, now));
                continue;
            }
            OptionItem item = existing.get(draft.id());
            if (item == null) {
                throw new BusinessRuleException("A opção informada não pertence a este grupo.");
            }
            item.apply(draft, position++, now);
            kept.add(item.getId());
        }
        for (OptionItem item : existing.values()) {
            if (!kept.contains(item.getId())) {
                item.retire(position++, now);
            }
        }
        options.sort(Comparator.comparingInt(OptionItem::getSortOrder));
    }

    public Optional<OptionItem> findOption(UUID optionId) {
        return options.stream().filter(option -> option.getId().equals(optionId)).findFirst();
    }

    public UUID getId() {
        return id;
    }

    public UUID getStoreId() {
        return storeId;
    }

    public String getName() {
        return name;
    }

    public int getMinChoices() {
        return minChoices;
    }

    public int getMaxChoices() {
        return maxChoices;
    }

    public PricingRule getPricingRule() {
        return pricingRule;
    }

    public boolean isActive() {
        return active;
    }

    public List<OptionItem> getOptions() {
        return Collections.unmodifiableList(options);
    }
}
