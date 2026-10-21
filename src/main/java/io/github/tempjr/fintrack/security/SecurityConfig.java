package io.github.tempjr.fintrack.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Segurança da API exposta na internet (ADR-007, ADR-008, RNF11).
 * <ul>
 *   <li>Toda rota exige o token, exceto health check (RNF14), /error e a documentação (desligada em prod).</li>
 *   <li>Sem sessão (cada requisição traz o token) e sem CSRF: CSRF protege cookies enviados pelo navegador;
 *       aqui não há cookie e o cliente é um app nativo.</li>
 *   <li>Sem login por formulário nem HTTP Basic.</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(SegurancaProperties.class)
public class SecurityConfig {

    private static final PathPatternRequestMatcher.Builder ROTA = PathPatternRequestMatcher.withDefaults();

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   SegurancaProperties properties,
                                                   ObjectMapper objectMapper,
                                                   @Value("${spring.h2.console.enabled:false}") boolean consoleH2)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new TokenAuthenticationFilter(properties), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(e -> e.authenticationEntryPoint(new ProblemDetailAuthenticationEntryPoint(objectMapper)))
                .authorizeHttpRequests(rotas -> {
                    rotas.requestMatchers(ROTA.matcher("/actuator/health"), ROTA.matcher("/actuator/health/**"))
                            .permitAll();
                    rotas.requestMatchers(ROTA.matcher("/error")).permitAll();
                    // Em prod o springdoc está desligado: essas rotas devolvem 404.
                    rotas.requestMatchers(ROTA.matcher("/v3/api-docs"), ROTA.matcher("/v3/api-docs/**"),
                            ROTA.matcher("/swagger-ui.html"), ROTA.matcher("/swagger-ui/**")).permitAll();
                    if (consoleH2) {
                        // Console do H2: outro servlet, fora do Spring MVC. Só existe no perfil dev.
                        rotas.requestMatchers(PathRequest.toH2Console()).permitAll();
                    }
                    rotas.anyRequest().authenticated();
                });
        if (consoleH2) {
            http.headers(h -> h.frameOptions(f -> f.sameOrigin())); // o console do H2 usa frames
        }
        return http.build();
    }

    /**
     * Sem este bean, o Spring Boot cria um usuário "user" com senha gerada e a imprime no log.
     * Não usamos usuário/senha: só o token.
     */
    @Bean
    public UserDetailsService semUsuariosPadrao() {
        return new InMemoryUserDetailsManager();
    }
}
