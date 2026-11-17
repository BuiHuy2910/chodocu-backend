package com.chodocu.backend.security;

import com.chodocu.backend.entity.User;
import com.chodocu.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    /** Giữ thông tin xác thực trong RAM 60 giây để mỗi API request không phải query lại bảng users trên Supabase. */
    private static final long CACHE_TTL_MS = 60_000L;

    private final UserRepository userRepository;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String key = normalize(email);
        long now = System.currentTimeMillis();

        CacheEntry hit = cache.get(key);
        if (hit != null && hit.expiresAt > now) {
            return hit.details;
        }

        User user = userRepository.findByEmail(key)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản với email: " + email));
        CustomUserDetails details = new CustomUserDetails(user);
        cache.put(key, new CacheEntry(details, now + CACHE_TTL_MS));
        return details;
    }

    /** Xóa cache ngay khi tài khoản bị khóa/mở khóa, đổi quyền hoặc đổi mật khẩu. */
    public void evict(String email) {
        if (email != null) {
            cache.remove(normalize(email));
        }
    }

    public void evictAll() {
        cache.clear();
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private record CacheEntry(CustomUserDetails details, long expiresAt) {}
}
