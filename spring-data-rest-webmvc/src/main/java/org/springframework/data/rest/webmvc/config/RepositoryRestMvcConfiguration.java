package org.springframework.data.rest.webmvc.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.data.repository.support.DomainClassConverter;
import org.springframework.data.rest.config.RepositoryRestConfiguration;
import org.springframework.data.rest.convert.ISO8601DateConverter;
import org.springframework.data.rest.convert.UUIDConverter;
import org.springframework.data.rest.repository.UriDomainClassConverter;
import org.springframework.data.rest.repository.context.AnnotatedHandlerBeanPostProcessor;
import org.springframework.data.rest.repository.context.RepositoriesFactoryBean;
import org.springframework.data.rest.repository.context.ValidatingRepositoryEventListener;
import org.springframework.data.rest.repository.json.PersistentEntityJackson2Module;
import org.springframework.data.rest.repository.json.PersistentEntityToJsonSchemaConverter;
import org.springframework.data.rest.repository.support.DomainObjectMerger;
import org.springframework.data.rest.webmvc.BaseUriMethodArgumentResolver;
import org.springframework.data.rest.webmvc.PagingAndSortingMethodArgumentResolver;
import org.springframework.data.rest.webmvc.PersistentEntityResourceHandlerMethodArgumentResolver;
import org.springframework.data.rest.webmvc.RepositoryController;
import org.springframework.data.rest.webmvc.RepositoryEntityController;
import org.springframework.data.rest.webmvc.RepositoryInformationHandlerMethodArgumentResolver;
import org.springframework.data.rest.webmvc.RepositoryRestController;
import org.springframework.data.rest.webmvc.RepositoryRestHandlerAdapter;
import org.springframework.data.rest.webmvc.RepositoryRestHandlerMapping;
import org.springframework.data.rest.webmvc.RepositoryRestRequestHandlerMethodArgumentResolver;
import org.springframework.data.rest.webmvc.RepositorySearchController;
import org.springframework.data.rest.webmvc.ServerHttpRequestMethodArgumentResolver;
import org.springframework.data.rest.webmvc.convert.JsonpResponseHttpMessageConverter;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor;
import org.springframework.util.ClassUtils;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;

/**
 * @author Jon Brisbin
 */
@Configuration
@ImportResource("classpath*:META-INF/spring-data-rest/**/*-export.xml")
public class RepositoryRestMvcConfiguration {

  private static final boolean IS_HIBERNATE4_MODULE_AVAILABLE = ClassUtils.isPresent(
      "com.fasterxml.jackson.datatype.hibernate4.Hibernate4Module",
      RepositoryRestMvcConfiguration.class.getClassLoader()
  );
  private static final boolean IS_JODA_MODULE_AVAILABLE       = ClassUtils.isPresent(
      "com.fasterxml.jackson.datatype.joda.JodaModule",
      RepositoryRestMvcConfiguration.class.getClassLoader()
  );

  @Bean public RepositoriesFactoryBean repositories() {
    return new RepositoriesFactoryBean();
  }

  @Bean public DefaultFormattingConversionService defaultConversionService() {
    DefaultFormattingConversionService conversionService = new DefaultFormattingConversionService();
    conversionService.addConverter(UUIDConverter.INSTANCE);
    conversionService.addConverter(ISO8601DateConverter.INSTANCE);
    configureConversionService(conversionService);
    return conversionService;
  }

  @Bean public DomainClassConverter<?> domainClassConverter() {
    return new DomainClassConverter<DefaultFormattingConversionService>(defaultConversionService());
  }

  @Bean public UriDomainClassConverter uriDomainClassConverter() {
    return new UriDomainClassConverter();
  }

  /**
   * {@link org.springframework.context.ApplicationListener} implementation for invoking {@link
   * org.springframework.validation.Validator} instances assigned to specific domain types.
   */
  @Bean public ValidatingRepositoryEventListener validatingRepositoryEventListener() {
    ValidatingRepositoryEventListener listener = new ValidatingRepositoryEventListener();
    configureValidatingRepositoryEventListener(listener);
    return listener;
  }

  /**
   * Main configuration for the REST exporter.
   */
  @Bean public RepositoryRestConfiguration config() {
    RepositoryRestConfiguration config = new RepositoryRestConfiguration();
    configureRepositoryRestConfiguration(config);
    return config;
  }

  /**
   * For getting access to the {@link javax.persistence.EntityManagerFactory}.
   *
   * @return
   */
  @Bean public PersistenceAnnotationBeanPostProcessor persistenceAnnotationBeanPostProcessor() {
    return new PersistenceAnnotationBeanPostProcessor();
  }

  /**
   * {@link org.springframework.beans.factory.config.BeanPostProcessor} to turn beans annotated as {@link
   * org.springframework.data.rest.repository.annotation.RepositoryEventHandler}s.
   *
   * @return
   */
  @Bean public AnnotatedHandlerBeanPostProcessor annotatedHandlerBeanPostProcessor() {
    return new AnnotatedHandlerBeanPostProcessor();
  }

  @Bean public DomainObjectMerger domainObjectMerger() throws Exception {
    return new DomainObjectMerger(
        repositories().getObject(),
        defaultConversionService()
    );
  }

  /**
   * The main REST exporter Spring MVC controller.
   *
   * @return
   */
  //  @Bean public RepositoryRestController repositoryRestController() {
  //    RepositoryRestController controller = new RepositoryRestController();
  //    configureRepositoryRestController(controller);
  //    return controller;
  //  }
  @Bean public RepositoryController repositoryController() throws Exception {
    return new RepositoryController(
        repositories().getObject(),
        config(),
        domainClassConverter(),
        defaultConversionService()
    );
  }

  @Bean public RepositoryEntityController repositoryEntityController() throws Exception {
    return new RepositoryEntityController(
        repositories().getObject(),
        config(),
        domainClassConverter(),
        defaultConversionService()
    );
  }

  @Bean public RepositorySearchController repositorySearchController() throws Exception {
    return new RepositorySearchController(
        repositories().getObject(),
        config(),
        domainClassConverter(),
        defaultConversionService()
    );
  }

  /**
   * Resolves the base {@link java.net.URI} under which this application is configured.
   *
   * @return
   */
  @Bean public BaseUriMethodArgumentResolver baseUriMethodArgumentResolver() {
    return new BaseUriMethodArgumentResolver();
  }

  @Bean public PagingAndSortingMethodArgumentResolver pagingAndSortingMethodArgumentResolver() {
    return new PagingAndSortingMethodArgumentResolver();
  }

  @Bean public ServerHttpRequestMethodArgumentResolver serverHttpRequestMethodArgumentResolver() {
    return new ServerHttpRequestMethodArgumentResolver();
  }

  @Bean public RepositoryInformationHandlerMethodArgumentResolver repoInfoMethodArgumentResolver() {
    return new RepositoryInformationHandlerMethodArgumentResolver();
  }

  @Bean public RepositoryRestRequestHandlerMethodArgumentResolver repoRequestArgumentResolver() {
    return new RepositoryRestRequestHandlerMethodArgumentResolver();
  }

  @Bean public PersistentEntityResourceHandlerMethodArgumentResolver persistentEntityArgumentResolver() {
    List<HttpMessageConverter<?>> messageConverters = new ArrayList<HttpMessageConverter<?>>();
    messageConverters.add(jacksonHttpMessageConverter());
    messageConverters.add(jsonpHttpMessageConverter());
    configureHttpMessageConverters(messageConverters);

    return new PersistentEntityResourceHandlerMethodArgumentResolver(messageConverters);
  }

  @Bean public PersistentEntityToJsonSchemaConverter jsonSchemaConverter() {
    return new PersistentEntityToJsonSchemaConverter();
  }

  @Bean public ObjectMapper objectMapper() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
    // Our special PersistentEntityResource Module
    objectMapper.registerModule(persistentEntityJackson2Module());
    // Hibernate types
    if(IS_HIBERNATE4_MODULE_AVAILABLE) {
      objectMapper.registerModule(new com.fasterxml.jackson.datatype.hibernate4.Hibernate4Module());
    }
    // JODA time
    if(IS_JODA_MODULE_AVAILABLE) {
      objectMapper.registerModule(new com.fasterxml.jackson.datatype.joda.JodaModule());
    }
    // Configure custom Modules
    configureJacksonObjectMapper(objectMapper);

    return objectMapper;
  }

  @Bean public MappingJackson2HttpMessageConverter jacksonHttpMessageConverter() {
    MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter();
    jacksonConverter.setObjectMapper(objectMapper());
    jacksonConverter.setSupportedMediaTypes(Arrays.asList(
        MediaType.APPLICATION_JSON,
        MediaType.valueOf("application/schema+json"),
        MediaType.valueOf("application/x-spring-data-verbose+json"),
        MediaType.valueOf("application/x-spring-data-compact+json")
    ));
    return jacksonConverter;
  }

  @Bean public JsonpResponseHttpMessageConverter jsonpHttpMessageConverter() {
    return new JsonpResponseHttpMessageConverter(jacksonHttpMessageConverter());
  }

  /**
   * Special {@link org.springframework.web.servlet.HandlerAdapter} that only recognizes handler methods defined in
   * the {@link RepositoryRestController} class.
   *
   * @return
   */
  @Bean public RepositoryRestHandlerAdapter repositoryExporterHandlerAdapter() {
    List<HttpMessageConverter<?>> messageConverters = new ArrayList<HttpMessageConverter<?>>();
    messageConverters.add(jacksonHttpMessageConverter());
    messageConverters.add(jsonpHttpMessageConverter());
    configureHttpMessageConverters(messageConverters);

    RepositoryRestHandlerAdapter handlerAdapter = new RepositoryRestHandlerAdapter();
    handlerAdapter.setMessageConverters(messageConverters);
    handlerAdapter.setCustomArgumentResolvers(
        Arrays.asList(baseUriMethodArgumentResolver(),
                      pagingAndSortingMethodArgumentResolver(),
                      serverHttpRequestMethodArgumentResolver(),
                      repoInfoMethodArgumentResolver(),
                      repoRequestArgumentResolver(),
                      persistentEntityArgumentResolver())
    );

    return handlerAdapter;
  }

  /**
   * Special {@link org.springframework.web.servlet.HandlerMapping} that only recognizes handler methods defined in
   * the
   * {@link RepositoryRestController} class.
   *
   * @return
   */
  @Bean public RepositoryRestHandlerMapping repositoryExporterHandlerMapping() {
    return new RepositoryRestHandlerMapping();
  }

  @Bean public PersistentEntityJackson2Module persistentEntityJackson2Module() {
    return new PersistentEntityJackson2Module(defaultConversionService());
  }

  /**
   * Bean for looking up methods annotated with {@link org.springframework.web.bind.annotation.ExceptionHandler}.
   *
   * @return
   */
  @Bean public ExceptionHandlerExceptionResolver exceptionHandlerExceptionResolver() {
    ExceptionHandlerExceptionResolver er = new ExceptionHandlerExceptionResolver();
    er.setCustomArgumentResolvers(
        Arrays.<HandlerMethodArgumentResolver>asList(
            baseUriMethodArgumentResolver(),
            serverHttpRequestMethodArgumentResolver(),
            repoInfoMethodArgumentResolver(),
            repoRequestArgumentResolver()
        )
    );

    List<HttpMessageConverter<?>> messageConverters = new ArrayList<HttpMessageConverter<?>>();
    messageConverters.add(jacksonHttpMessageConverter());
    messageConverters.add(jsonpHttpMessageConverter());
    configureHttpMessageConverters(messageConverters);

    er.setMessageConverters(messageConverters);
    configureExceptionHandlerExceptionResolver(er);

    return er;
  }

  /**
   * Override this method to add additional configuration.
   *
   * @param config
   *     Main configuration bean.
   */
  protected void configureRepositoryRestConfiguration(RepositoryRestConfiguration config) {
  }

  /**
   * Override this method to add your own converters.
   *
   * @param conversionService
   *     Default ConversionService bean.
   */
  protected void configureConversionService(ConfigurableConversionService conversionService) {
  }

  /**
   * Override this method to add validators manually.
   *
   * @param validatingListener
   *     The {@link org.springframework.context.ApplicationListener} responsible for invoking {@link
   *     org.springframework.validation.Validator} instances.
   */
  protected void configureValidatingRepositoryEventListener(ValidatingRepositoryEventListener validatingListener) {
  }

  /**
   * Configure the REST controller directly.
   *
   * @param controller
   *     The {@link RepositoryRestController} instance.
   */
  protected void configureRepositoryRestController(RepositoryRestController controller) {
  }

  /**
   * Configure the {@link ExceptionHandlerExceptionResolver}.
   *
   * @param exceptionResolver
   *     The default exception resolver on which you can add custom argument resolvers.
   */
  protected void configureExceptionHandlerExceptionResolver(ExceptionHandlerExceptionResolver exceptionResolver) {
  }

  protected void configureHttpMessageConverters(List<HttpMessageConverter<?>> messageConverters) {
  }

  protected void configureJacksonObjectMapper(ObjectMapper objectMapper) {
  }

}
