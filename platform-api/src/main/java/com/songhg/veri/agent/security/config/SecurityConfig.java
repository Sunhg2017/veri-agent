package com.songhg.veri.agent.security.config;

import com.songhg.veri.agent.auth.application.AuthProperties;
import com.songhg.veri.agent.auth.config.BearerTokenAuthenticationFilter;
import com.songhg.veri.agent.auth.config.PasswordChangeRequiredFilter;
import com.songhg.veri.agent.asset.config.AssetProperties;
import com.songhg.veri.agent.common.audit.AuditRetentionProperties;
import com.songhg.veri.agent.common.secret.SecretProviderProperties;
import com.songhg.veri.agent.common.security.ServiceCallerProperties;
import com.songhg.veri.agent.common.security.ServiceTokenAuthenticationFilter;
import com.songhg.veri.agent.documentinput.config.DocumentInputProperties;
import com.songhg.veri.agent.integration.application.PlatformIntegrationProperties;
import com.songhg.veri.agent.management.config.ManagementProperties;
import com.songhg.veri.agent.modelaccess.config.ModelAccessProperties;
import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({AuthProperties.class, PlatformIntegrationProperties.class, ModelAccessProperties.class, AssetProperties.class, DocumentInputProperties.class, TestDesignProperties.class, SecretProviderProperties.class, AuditRetentionProperties.class, ManagementProperties.class, ServiceCallerProperties.class})
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ServiceTokenAuthenticationFilter serviceTokenAuthenticationFilter,
            ObjectProvider<BearerTokenAuthenticationFilter> bearerTokenAuthenticationFilterProvider,
            ObjectProvider<PasswordChangeRequiredFilter> passwordChangeRequiredFilterProvider
    ) throws Exception {
        http.addFilterBefore(
                serviceTokenAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class
        );
        bearerTokenAuthenticationFilterProvider.ifAvailable(filter -> http.addFilterBefore(
                filter,
                UsernamePasswordAuthenticationFilter.class
        ));
        passwordChangeRequiredFilterProvider.ifAvailable(filter -> http.addFilterAfter(
                filter,
                BearerTokenAuthenticationFilter.class
        ));

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(registry -> registry
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info", "/actuator/metrics/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/model-access/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/asset/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/document-input/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/test-design/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/document-input/webhooks/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/examples/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/contexts/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/audit/events").permitAll()
                        .anyRequest().authenticated()
                )
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .build();
    }

    @Bean
    UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException(username);
        };
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
