package com.shark.profile.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    @Value("${internal.secret}")
    private String internalSecret;

    @Bean
    public FilterRegistrationBean<InternalHeaderFilter> internalHeaderFilter() {
        FilterRegistrationBean<InternalHeaderFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new InternalHeaderFilter());
        registrationBean.addUrlPatterns("/api/profiles/*", "/api/rankings/*");
        registrationBean.setOrder(1); // Menor orden = se ejecuta primero
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<InternalOnlyFilter> internalOnlyFilter() {
        FilterRegistrationBean<InternalOnlyFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new InternalOnlyFilter(internalSecret));
        registrationBean.addUrlPatterns("/api/profiles/internal/*");
        registrationBean.setOrder(2);
        return registrationBean;
    }
}
