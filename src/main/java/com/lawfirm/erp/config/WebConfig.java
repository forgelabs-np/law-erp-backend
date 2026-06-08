package com.lawfirm.erp.config;

import com.lawfirm.erp.security.FirmInterceptor;
import com.lawfirm.erp.security.HibernateFilterEnabler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final FirmInterceptor firmInterceptor;
    private final HibernateFilterEnabler hibernateFilterEnabler;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(firmInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(
                        "/api/v1/auth/**",
                        "/api/v1/super-admin/register",
                        "/api/v1/super-admin/login",
                        "/swagger-ui/**",
                        "/v3/api-docs/**"
                );

        registry.addInterceptor(hibernateFilterEnabler)
                .addPathPatterns("/api/v1/**");
    }
}