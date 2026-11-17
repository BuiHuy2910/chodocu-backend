package com.chodocu.backend.controller;

import com.chodocu.backend.dto.ApiResponse;
import com.chodocu.backend.entity.Category;
import com.chodocu.backend.entity.University;
import com.chodocu.backend.repository.CategoryRepository;
import com.chodocu.backend.repository.UniversityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CategoryUniversityController {

    private static final long CACHE_TTL_MS = 10 * 60_000L; // 10 phút

    private final CategoryRepository categoryRepository;
    private final UniversityRepository universityRepository;

    private volatile List<Category> cachedCategories;
    private volatile long categoriesExpireAt;

    private volatile List<University> cachedUniversities;
    private volatile long universitiesExpireAt;

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<Category>>> getAllCategories() {
        long now = System.currentTimeMillis();
        List<Category> list = cachedCategories;
        if (list == null || now >= categoriesExpireAt) {
            list = List.copyOf(categoryRepository.findAll());
            cachedCategories = list;
            categoriesExpireAt = now + CACHE_TTL_MS;
        }
        return ResponseEntity.ok(ApiResponse.ok("Danh sách danh mục", list));
    }

    @GetMapping("/universities")
    public ResponseEntity<ApiResponse<List<University>>> getAllUniversities() {
        long now = System.currentTimeMillis();
        List<University> list = cachedUniversities;
        if (list == null || now >= universitiesExpireAt) {
            list = List.copyOf(universityRepository.findAll());
            cachedUniversities = list;
            universitiesExpireAt = now + CACHE_TTL_MS;
        }
        return ResponseEntity.ok(ApiResponse.ok("Danh sách trường đại học & KTX", list));
    }
}
