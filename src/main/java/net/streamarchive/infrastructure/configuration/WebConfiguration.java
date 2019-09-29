package net.streamarchive.infrastructure.configuration;


import net.streamarchive.infrastructure.SettingsProperties;
import net.streamarchive.infrastructure.handlers.db.ArchiveDBHandler;
import net.streamarchive.infrastructure.handlers.db.EnabledDBHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@EnableScheduling
@RestController
@Configuration
@PropertySource("file:${user.home}/application.properties")
public class WebConfiguration extends WebSecurityConfigurerAdapter {

    @Autowired
    SettingsProperties settingsProperties;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .antMatcher("/**")
                .authorizeRequests()
                .antMatchers("/login**", "/handler/**", "/callback/", "/webjars/**", "/error**", "/status**")
                .permitAll()
                .anyRequest()
                .authenticated().and().logout().logoutSuccessUrl("/").clearAuthentication(true).and().csrf().disable().httpBasic();
        ;
    }

    @RequestMapping("/unauthenticated")
    public String unauthenticated() {
        return "redirect:/?error=true";
    }

    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
        auth.inMemoryAuthentication()
                .withUser("user1").password(passwordEncoder().encode("user1Pass")).authorities("ROLE_USER");
        ;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    @Bean
    RestTemplate restTemplateWithCredentials(RestTemplateBuilder restTemplateBuilder) {
        return restTemplateBuilder.basicAuthentication(settingsProperties.getDbUsername(), settingsProperties.getDbPassword()).build();
    }

    @Bean
    ArchiveDBHandler archiveDBHandler() {
        return new EnabledDBHandler();
    }

}
