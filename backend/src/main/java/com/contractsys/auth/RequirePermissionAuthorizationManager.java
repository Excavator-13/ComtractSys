package com.contractsys.auth;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class RequirePermissionAuthorizationManager implements AuthorizationManager<MethodInvocation> {
    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, MethodInvocation invocation) {
        RequirePermission permission = findPermission(invocation);
        if (permission == null || permission.value().length == 0) {
            return new AuthorizationDecision(true);
        }

        Authentication current = authentication.get();
        if (current == null || !current.isAuthenticated()) {
            return new AuthorizationDecision(false);
        }

        Set<String> authorities = current.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        boolean granted = Arrays.stream(permission.value()).anyMatch(authorities::contains);
        return new AuthorizationDecision(granted);
    }

    private RequirePermission findPermission(MethodInvocation invocation) {
        Method method = invocation.getMethod();
        RequirePermission permission = AnnotatedElementUtils.findMergedAnnotation(method, RequirePermission.class);
        if (permission != null) {
            return permission;
        }
        Class<?> targetClass = invocation.getThis() == null
                ? method.getDeclaringClass()
                : AopUtils.getTargetClass(invocation.getThis());
        return AnnotatedElementUtils.findMergedAnnotation(targetClass, RequirePermission.class);
    }
}
