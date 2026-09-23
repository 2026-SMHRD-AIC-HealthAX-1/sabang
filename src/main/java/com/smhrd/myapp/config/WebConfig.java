package com.smhrd.myapp.config;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// 전표 사진(/uploads/**)은 camera.py가 실행 중에 src/main/resources/static/uploads에 계속 새로 저장한다.
// 그런데 Spring은 static 파일을 빌드 결과물(target/classes/static)에서 서빙하기 때문에, 재빌드 전까지는
// 새로 찍힌 사진이 404가 나서 대시보드/전표 화면에 안 보였다. 그래서 /uploads/**만 실제 저장 폴더를 직접 보게 한다.
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadDir = Paths.get(System.getProperty("user.dir"), "src", "main", "resources", "static", "uploads")
                .toAbsolutePath().normalize();

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadDir.toUri().toString());
    }
}
