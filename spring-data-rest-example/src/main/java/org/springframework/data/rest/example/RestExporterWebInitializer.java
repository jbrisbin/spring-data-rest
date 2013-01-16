package org.springframework.data.rest.example;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;

import org.springframework.data.rest.example.gemfire.GemfireRepositoryConfig;
import org.springframework.data.rest.example.jpa.JpaRepositoryConfig;
import org.springframework.data.rest.example.mongodb.MongoDbRepositoryConfig;
import org.springframework.data.rest.webmvc.RepositoryRestDispatcherServlet;
import org.springframework.web.WebApplicationInitializer;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

/**
 * @author Jon Brisbin
 */
public class RestExporterWebInitializer implements WebApplicationInitializer {

  @Override public void onStartup(ServletContext servletContext) throws ServletException {
    AnnotationConfigWebApplicationContext rootCtx = new AnnotationConfigWebApplicationContext();
    rootCtx.register(
        JpaRepositoryConfig.class,
        MongoDbRepositoryConfig.class,
        GemfireRepositoryConfig.class
    );

    servletContext.addListener(new ContextLoaderListener(rootCtx));

    AnnotationConfigWebApplicationContext webCtx = new AnnotationConfigWebApplicationContext();
    webCtx.register(RestExporterExampleRestConfig.class);

    RepositoryRestDispatcherServlet dispatcherServlet = new RepositoryRestDispatcherServlet(webCtx);
    ServletRegistration.Dynamic reg = servletContext.addServlet("rest-exporter", dispatcherServlet);
    reg.setLoadOnStartup(1);
    reg.addMapping("/*");
  }

}
