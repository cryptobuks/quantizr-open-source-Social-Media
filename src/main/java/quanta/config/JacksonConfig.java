package quanta.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Jackson JSON stuff.
 * 
 * see: spring.jackson.serialization.indent_output=true
 */
@Configuration
public class JacksonConfig {

    // This never worked keeping just for future reference.
    // extends WebMvcConfigurationSupport {
    // @Override
    // protected void extendMessageConverters(List<HttpMessageConverter<?>>
    // converters) {
    // super.addDefaultHttpMessageConverters(converters);
    // for (HttpMessageConverter<?> converter : converters) {
    // if (converter instanceof MappingJackson2HttpMessageConverter) {
    // MappingJackson2HttpMessageConverter jacksonConverter =
    // (MappingJackson2HttpMessageConverter) converter;
    // jacksonConverter.setPrettyPrint(true);
    // }
    // }
    // }

    @Bean
    public MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter() {
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter();
        ObjectMapper objectMapper = new ObjectMapper();
        // objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
        // false);

        jsonConverter.setObjectMapper(objectMapper);
        jsonConverter.setPrettyPrint(true);
        return jsonConverter;
    }
}
