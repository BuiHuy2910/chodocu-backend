package com.chodocu.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class ChodocuBackendApplication {

    public static void main(String[] args) {
        // Mọi thời điểm lưu trong DB là giờ UTC (giống các hàm SQL của chế độ Supabase).
        // Khi hiển thị cho người dùng mới đổi sang giờ Việt Nam (xem DisplayFormat).
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(ChodocuBackendApplication.class, args);
    }
}
