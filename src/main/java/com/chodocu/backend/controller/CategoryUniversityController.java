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

    private final CategoryRepository categoryRepository;
    private final UniversityRepository universityRepository;

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<Category>>> getAllCategories() {
        return ResponseEntity.ok(ApiResponse.ok("Danh sách danh mục", categoryRepository.findAll()));
    }

    @GetMapping("/universities")
    public ResponseEntity<ApiResponse<List<University>>> getAllUniversities() {
        return ResponseEntity.ok(ApiResponse.ok("Danh sách trường đại học & KTX", universityRepository.findAll()));
    }
}
