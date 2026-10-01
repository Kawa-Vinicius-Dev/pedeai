package com.pedeai.storefront.dto;

import java.util.List;
import java.util.UUID;

public record MenuCategoryResponse(UUID id, String name, List<MenuProductResponse> products) {
}
