package com.pedeai.printing.config;

import com.pedeai.printing.service.AgentService;
import com.pedeai.shared.security.JsonSecurityErrorHandler;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * /api/agent/** fica numa cadeia própria: só aceita o token de dispositivo do agente, e o token do agente não
 * serve em nenhuma outra rota (a cadeia principal só aceita o JWT de pessoa).
 */
@Configuration
@org.springframework.boot.context.properties.EnableConfigurationProperties(AgentProperties.class)
public class AgentSecurityConfig {
    private static final String BEARER = "Bearer ";

    @Bean
    @Order(1)
    SecurityFilterChain agentSecurityFilterChain(HttpSecurity http, AgentService agentService,
                                                 JsonSecurityErrorHandler errorHandler) throws Exception {
        http
                .securityMatcher("/api/agent/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/agent/pairings").permitAll()
                        .anyRequest().hasRole("AGENT"))
                .addFilterBefore(new AgentTokenFilter(agentService), AnonymousAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler));
        return http.build();
    }

    private static final class AgentTokenFilter extends OncePerRequestFilter {
        private final AgentService agentService;

        AgentTokenFilter(AgentService agentService) {
            this.agentService = agentService;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.startsWith(BEARER)) {
                agentService.authenticate(header.substring(BEARER.length()).trim()).ifPresent(agent ->
                        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken
                                .authenticated(agent, null, List.of(new SimpleGrantedAuthority("ROLE_AGENT")))));
            }
            chain.doFilter(request, response);
        }
    }
}
