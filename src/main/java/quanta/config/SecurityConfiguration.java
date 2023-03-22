package quanta.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.BeanIds;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import lombok.extern.slf4j.Slf4j;
import quanta.AppController;
import quanta.filter.AuditFilter;
import quanta.mongo.MongoAuth;

/**
 * Spring security support
 */
// @EnableWebSecurity(debug = true) // #DEBUG-SECURITY
@EnableWebSecurity
@Slf4j 
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {
    @Autowired
    AuditFilter auditFilter;

    @Autowired
    UserDetailsService userDetailsService;

    @Autowired
	protected MongoAuth auth;

    @Bean(name = BeanIds.AUTHENTICATION_MANAGER)
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        log.debug("Configuring UserDetailsService: " + userDetailsService.getClass().getName());
        auth.userDetailsService(userDetailsService);
    }

    @Override
    public void configure(WebSecurity web) throws Exception {
        // web.debug(true); // #DEBUG-SECURITY

        web.ignoring() //
                .antMatchers(AppController.API_PATH + "/getConfig") //
                .antMatchers(AppController.API_PATH + "/login") //
                .antMatchers(AppController.API_PATH + "/bin/**") //
                .antMatchers(AppController.API_PATH + "/anonPageLoad") //
                .antMatchers(AppController.API_PATH + "/getUserProfile") //
                .antMatchers(AppController.API_PATH + "/serverPush") //
                .antMatchers("/public") //
                .antMatchers("/error") //
                .antMatchers("/");
    }

    // .authenticationEntryPoint(restAuthenticationEntryPoint)
    // .antMatchers("/anonymous*").anonymous()
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        // mysteriously we're setting all users as ROLE_USER but somehow only anonymous works here.
        http.csrf().disable() //
                .antMatcher(AppController.API_PATH + "/**").authorizeRequests().anyRequest().hasRole("ANONYMOUS");

        // The mess below was all experimenting that I'd like to keep commented for now but may never be
        // needed.
        //
        // http.authorizeRequests() //
        // NOTE: Spring auto-prefixes "ROLE_" so this is really "ROLE_USER" here;
        // .antMatchers(AppController.API_PATH + "/*").hasRole("USER")
        // .antMatchers("/user").hasAnyRole("ADMIN", "USER")
        // .antMatchers("/").permitAll()
        // .antMatchers(AppController.API_PATH + "/**").permitAll().anyRequest().authenticated()
        // .and().addFilterBefore(auditFilter, BasicAuthenticationFilter.class);
    }

    /*
     * This NoOpPasswordEncoder is misleading, because we DO use HASHED passwords, but for now I'm doing
     * the hash before passing thru to spring, so this needs to be a noop
     */
    @Bean
    public PasswordEncoder getPasswordEncoder() {
        return NoOpPasswordEncoder.getInstance();
    }
}
