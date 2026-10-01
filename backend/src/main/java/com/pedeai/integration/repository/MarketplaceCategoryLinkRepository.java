package com.pedeai.integration.repository;

import com.pedeai.integration.domain.MarketplaceCategoryLink;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplaceCategoryLinkRepository
        extends JpaRepository<MarketplaceCategoryLink, MarketplaceCategoryLink.Key> {
}
