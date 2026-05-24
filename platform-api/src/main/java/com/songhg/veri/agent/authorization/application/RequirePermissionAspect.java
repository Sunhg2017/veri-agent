package com.songhg.veri.agent.authorization.application;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Applies permission annotations and optional resource-scope checks before secured methods run.
 */
@Aspect
@Component
public class RequirePermissionAspect {

    private final AuthorizationService authorizationService;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final BeanFactoryResolver beanFactoryResolver;

    public RequirePermissionAspect(AuthorizationService authorizationService, BeanFactory beanFactory) {
        this.authorizationService = authorizationService;
        this.beanFactoryResolver = new BeanFactoryResolver(beanFactory);
    }

    @Before("@annotation(requirePermission)")
    public void requirePermission(JoinPoint joinPoint, RequirePermission requirePermission) {
        AuthUserPrincipal principal = authorizationService.requireCurrent(requirePermission.value());
        // Service principals intentionally keep the existing internal-call bypass for scoped checks.
        if (principal == null || !StringUtils.hasText(requirePermission.scope())) {
            return;
        }
        for (ResourceScope scope : resolveScopes(joinPoint, requirePermission)) {
            authorizationService.require(principal, requirePermission.value(), scope);
        }
    }

    @Before("@annotation(requirePermissions)")
    public void requirePermissions(JoinPoint joinPoint, RequirePermissions requirePermissions) {
        for (RequirePermission requirePermission : requirePermissions.value()) {
            requirePermission(joinPoint, requirePermission);
        }
    }

    private List<ResourceScope> resolveScopes(JoinPoint joinPoint, RequirePermission requirePermission) {
        Object value = expressionParser.parseExpression(requirePermission.scope())
                .getValue(evaluationContext(joinPoint));
        List<ResourceScope> scopes = new ArrayList<>();
        collectScopes(value, requirePermission.scopeType(), scopes);
        // An empty expression result falls back to platform scope, matching previous list-query behavior.
        return scopes.isEmpty() ? List.of(ResourceScope.platform()) : List.copyOf(scopes);
    }

    private StandardEvaluationContext evaluationContext(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        StandardEvaluationContext context = new StandardEvaluationContext(joinPoint.getTarget());
        context.setBeanResolver(beanFactoryResolver);
        context.setVariable("target", joinPoint.getTarget());
        context.setVariable("args", args);
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        for (int i = 0; i < args.length; i++) {
            context.setVariable("p" + i, args[i]);
            context.setVariable("a" + i, args[i]);
            if (parameterNames != null && i < parameterNames.length) {
                context.setVariable(parameterNames[i], args[i]);
            }
        }
        return context;
    }

    private void collectScopes(Object value, String scopeType, List<ResourceScope> scopes) {
        if (value == null) {
            return;
        }
        if (value instanceof ResourceScope resourceScope) {
            scopes.add(resourceScope);
            return;
        }
        if (value instanceof Collection<?> collection) {
            collection.forEach(item -> collectScopes(item, scopeType, scopes));
            return;
        }
        if (value.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(value); i++) {
                collectScopes(Array.get(value, i), scopeType, scopes);
            }
            return;
        }
        String scopeId = value.toString();
        if (StringUtils.hasText(scopeId)) {
            scopes.add(new ResourceScope(scopeType, scopeId));
        }
    }
}
