package app.trashbin.config;

import app.trashbin.auth.service.FixedWindowRateLimiter;
import app.trashbin.auth.web.AuthRateLimitFilter;
import app.trashbin.common.error.ProblemResponseWriter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * Deny-by-default security (docs/SECURITY_GUIDELINES.md section 4): every route needs a valid
 * access token except the explicit allow-list below. Actuator endpoints other than health are
 * admin-only.
 */
@Configuration(proxyBeanMethods = false)
// Not needed (and not buildable) in the web-less recovery command (application-recovery.yml).
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, ProblemResponseWriter problems, FixedWindowRateLimiter rateLimiter)
            throws Exception {
        http
                // Bearer-token API: browsers do not attach the Authorization header cross-site, so
                // CSRF tokens add nothing. The cookie-reading endpoints (refresh, logout) are
                // guarded by SameSite=Strict plus RefreshCookie.requireSameOrigin.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                HttpMethod.POST,
                                                "/api/v1/auth/register",
                                                "/api/v1/auth/login",
                                                "/api/v1/auth/refresh",
                                                "/api/v1/auth/logout")
                                        .permitAll()
                                        // Authenticated by the signed ticket in the path instead.
                                        .requestMatchers(HttpMethod.GET, "/api/v1/content/**")
                                        .permitAll()
                                        .requestMatchers(
                                                "/api/v1/health",
                                                "/actuator/health",
                                                "/actuator/health/**",
                                                "/v3/api-docs",
                                                "/v3/api-docs/**",
                                                "/swagger-ui.html",
                                                "/swagger-ui/**")
                                        .permitAll()
                                        .requestMatchers("/actuator/**", "/api/v1/admin/**")
                                        .hasRole("ADMIN")
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(
                        oauth ->
                                oauth.jwt(
                                                jwt ->
                                                        jwt.jwtAuthenticationConverter(
                                                                jwtAuthenticationConverter()))
                                        .authenticationEntryPoint(
                                                problems.authenticationEntryPoint())
                                        .accessDeniedHandler(problems.accessDeniedHandler()))
                .exceptionHandling(
                        e ->
                                e.authenticationEntryPoint(problems.authenticationEntryPoint())
                                        .accessDeniedHandler(problems.accessDeniedHandler()))
                .addFilterBefore(
                        new AuthRateLimitFilter(rateLimiter, problems),
                        BearerTokenAuthenticationFilter.class)
                .headers(
                        h ->
                                h.referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER))
                                        // Previews are framed by our own pages (same origin).
                                        .frameOptions(f -> f.sameOrigin()));
        return http.build();
    }

    /** Maps the token's {@code roles} claim to {@code ROLE_*} authorities. */
    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
