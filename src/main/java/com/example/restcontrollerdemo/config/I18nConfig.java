package com.example.restcontrollerdemo.config;

import jakarta.validation.MessageInterpolator;
import java.util.Locale;
import org.hibernate.validator.messageinterpolation.ResourceBundleMessageInterpolator;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MessageSourceResourceBundleLocator;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/** English by default; {@code Accept-Language: fr} for French. */
@Configuration
public class I18nConfig {

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasename("classpath:messages");
        source.setDefaultEncoding("UTF-8");
        // Unsupported locales fall back to messages.properties, not to the server's system locale.
        source.setFallbackToSystemLocale(false);
        return source;
    }

    /** Bean Validation messages go through the same {@link MessageSource}, so they are localized too. */
    @Bean
    public LocalValidatorFactoryBean validator(MessageSource messageSource) {
        MessageInterpolator interpolator = new LocaleAwareMessageInterpolator(
                new ResourceBundleMessageInterpolator(new MessageSourceResourceBundleLocator(messageSource)));
        LocalValidatorFactoryBean factory = new LocalValidatorFactoryBean();
        factory.setMessageInterpolator(interpolator);
        return factory;
    }

    /** The bean name {@code localeResolver} replaces Spring Boot's default. No header means English. */
    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(Locale.ENGLISH);
        return resolver;
    }

    /**
     * Interpolates in the request's locale rather than the JVM's. Spring MVC binds the locale to the request thread
     * ({@link LocaleContextHolder}) before {@code @Valid} runs, so messages come out already localized.
     */
    private record LocaleAwareMessageInterpolator(MessageInterpolator delegate) implements MessageInterpolator {

        @Override
        public String interpolate(String messageTemplate, Context context) {
            return delegate.interpolate(messageTemplate, context, LocaleContextHolder.getLocale());
        }

        @Override
        public String interpolate(String messageTemplate, Context context, Locale locale) {
            return delegate.interpolate(messageTemplate, context, locale);
        }
    }
}
