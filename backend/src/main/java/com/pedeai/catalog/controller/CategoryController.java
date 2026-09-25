package com.pedeai.catalog.controller;

import com.pedeai.catalog.dto.CategoryRequest;
import com.pedeai.catalog.dto.CategoryResponse;
import com.pedeai.catalog.service.CategoryService;
import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.security.Permissions;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public List<CategoryResponse> list(CurrentUser user) {
        return categoryService.list(user.storeId());
    }

    @GetMapping("/{id}")
    public CategoryResponse get(CurrentUser user, @PathVariable UUID id) {
        return categoryService.get(user.storeId(), id);
    }

    @PostMapping
    @PreAuthorize(Permissions.MANAGE_CATALOG)
    public ResponseEntity<CategoryResponse> create(CurrentUser user, @Valid @RequestBody CategoryRequest request) {
        CategoryResponse created = categoryService.create(user.storeId(), request);
        return ResponseEntity.created(URI.create("/api/categories/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permissions.MANAGE_CATALOG)
    public CategoryResponse update(CurrentUser user, @PathVariable UUID id,
                                   @Valid @RequestBody CategoryRequest request) {
        return categoryService.update(user.storeId(), id, request);
    }
}
