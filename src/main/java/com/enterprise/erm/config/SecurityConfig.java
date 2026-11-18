package com.enterprise.erm.config;

import java.util.ArrayList;
import java.util.List;

import com.enterprise.erm.service.AuditService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.ldap.DefaultSpringSecurityContextSource;
import org.springframework.security.ldap.authentication.BindAuthenticator;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.HttpSessionEventPublisher;

/**
 * Role model (one role per login, stored in app_users.role):
 *   ROLE_ADMIN    - system administrator: manages people, logins and all data, sees audit log, verifies credentials. Cannot approve.
 *   ROLE_MANAGER  - PMO / project manager: manages projects, resources, tasks, skills, RACI.
 *   ROLE_APPROVER - department head: the ONLY role that can approve / reject (maker-checker).
 *   ROLE_AUDITOR  - read-only access to everything, including reports and the audit log.
 *   ROLE_USER     - employee: sees own tasks, skills and RACI entries plus the project list.
 */
@Configuration
@EnableMethodSecurity   // makes the @PreAuthorize rules on controllers effective (they were ignored before)
public class SecurityConfig {

    private static final String[] READERS = {"ADMIN", "MANAGER", "APPROVER", "AUDITOR"};
    private static final String[] MANAGERS = {"ADMIN", "MANAGER"};

    @Value("${app.security.ldap.enabled:false}")
    private boolean ldapEnabled;

    @Value("${app.security.ldap.url:ldap://localhost:389}")
    private String ldapUrl;

    @Value("${app.security.ldap.base:dc=example,dc=com}")
    private String ldapBase;

    @Value("${app.security.ldap.user-dn-pattern:uid={0}}")
    private String userDnPattern;

    // static: the encoder no longer needs a SecurityConfig instance, so other beans
    // (and this class) can depend on it without creating a cycle
    @Bean
    static PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    // Dependencies come in as method parameters, not as fields of this class
    @Bean
    AuthenticationManager authenticationManager(LocalUserDetailsService localUsers,
                                                PasswordEncoder encoder,
                                                ObjectProvider<AuthenticationEventPublisher> publisher) {
        List<AuthenticationProvider> providers = new ArrayList<>();

        DaoAuthenticationProvider local = new DaoAuthenticationProvider(localUsers);
        local.setPasswordEncoder(encoder);
        providers.add(local);

        if (ldapEnabled) {
            DefaultSpringSecurityContextSource context =
                    new DefaultSpringSecurityContextSource(ldapUrl + "/" + ldapBase);
            context.afterPropertiesSet();
            BindAuthenticator bind = new BindAuthenticator(context);
            bind.setUserDnPatterns(new String[] { userDnPattern });
            providers.add(new LdapAuthenticationProvider(bind));
        }
        ProviderManager manager = new ProviderManager(providers);
        // needed so login success / failure events reach SecurityAuditListener
        publisher.ifAvailable(manager::setAuthenticationEventPublisher);
        return manager;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, AuditService audit) throws Exception {
        http.authorizeHttpRequests(a -> a
                        .requestMatchers("/login", "/error", "/css/**", "/js/**", "/vendor/**").permitAll()
                        .requestMatchers("/denied").authenticated()
                        // audit log and user administration
                        .requestMatchers("/audit/**").hasAnyRole("ADMIN", "AUDITOR")
                        .requestMatchers("/users/**").hasRole("ADMIN")
                        // writes (most specific first)
                        .requestMatchers(HttpMethod.POST, "/credentials/*/verify").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/approvals/request").hasAnyRole(MANAGERS)
                        .requestMatchers(HttpMethod.POST, "/approvals/**").hasRole("APPROVER")
                        // governance: everyone with people-data access may read; only ADMIN/MANAGER may write;
                        // evidence verification (maker-checker) is the one write open to approvers/auditors
                        .requestMatchers(HttpMethod.POST, "/governance/evidence/*/verify").hasAnyRole("ADMIN", "APPROVER", "AUDITOR")
                        .requestMatchers(HttpMethod.POST, "/governance/**").hasAnyRole(MANAGERS)
                        .requestMatchers("/governance/**", "/evidence/**").hasAnyRole(READERS)
                        // any signed-in user may hit this URL; HomeController checks "assignee or manager"
                        .requestMatchers(HttpMethod.POST, "/work/*/status").authenticated()
                        .requestMatchers(HttpMethod.POST, "/employees/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/departments/**", "/units/**", "/resources/**", "/projects/**",
                                "/skills/**", "/raci/**", "/work/**").hasAnyRole(MANAGERS)
                        .requestMatchers("/assignments/**").hasAnyRole(MANAGERS)
                        .requestMatchers(HttpMethod.GET, "/departments/new", "/departments/*/edit", "/units/new", "/units/*/edit").hasAnyRole(MANAGERS)
                        // people / governance data: not for plain employees
                        .requestMatchers("/approvals/**", "/reports/**", "/employees/**", "/resources/**", "/departments/**", "/units/**")
                                .hasAnyRole(READERS)
                        .anyRequest().authenticated())
                .formLogin(f -> f.loginPage("/login").defaultSuccessUrl("/", true).permitAll())
                .logout(l -> l.logoutSuccessUrl("/login?logout").deleteCookies("JSESSIONID"))
                .sessionManagement(s -> s.maximumSessions(1))
                .exceptionHandling(e -> e.accessDeniedHandler((req, res, ex) -> {
                    try {
                        Authentication au = SecurityContextHolder.getContext().getAuthentication();
                        audit.record(au == null ? "anonymous" : au.getName(), "ACCESS_DENIED", "HTTP", null,
                                req.getMethod() + " " + req.getRequestURI(), req.getRemoteAddr());
                    } catch (Exception ignored) {
                        // auditing must never block the response
                    }
                    res.sendRedirect(req.getContextPath() + "/denied");
                }));
        http.headers(h -> h
                .frameOptions(f -> f.deny())
                .contentSecurityPolicy(c -> c.policyDirectives(
                        "default-src 'self'; "
                                + "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; "
                                + "font-src 'self' https://cdn.jsdelivr.net; "
                                + "img-src 'self' data:; script-src 'self'; "
                                + "form-action 'self'; frame-ancestors 'none'")));
        // CSRF protection stays ON for every form (the H2 console exception was removed with the console)
        return http.build();
    }
}
