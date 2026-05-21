package com.contractsys.config;

import com.contractsys.auth.RequirePermission;
import com.contractsys.auth.RequirePermissionAuthorizationManager;
import org.springframework.aop.Advisor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;

@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static RequirePermissionAuthorizationManager requirePermissionAuthorizationManager() {
        return new RequirePermissionAuthorizationManager();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static Advisor requirePermissionAdvisor(RequirePermissionAuthorizationManager authorizationManager) {
        Pointcut classPointcut = AnnotationMatchingPointcut.forClassAnnotation(RequirePermission.class);
        Pointcut methodPointcut = AnnotationMatchingPointcut.forMethodAnnotation(RequirePermission.class);
        ComposablePointcut pointcut = new ComposablePointcut(classPointcut).union(methodPointcut);
        return new AuthorizationManagerBeforeMethodInterceptor(pointcut, authorizationManager);
    }
}
