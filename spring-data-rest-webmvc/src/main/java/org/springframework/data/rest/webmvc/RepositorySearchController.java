package org.springframework.data.rest.webmvc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.core.MethodParameter;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.repository.support.DomainClassConverter;
import org.springframework.data.repository.support.Repositories;
import org.springframework.data.rest.config.RepositoryRestConfiguration;
import org.springframework.data.rest.config.ResourceMapping;
import org.springframework.data.rest.repository.invoke.RepositoryMethod;
import org.springframework.data.rest.repository.invoke.RepositoryMethodInvoker;
import org.springframework.data.rest.webmvc.support.JsonpResponse;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * @author Jon Brisbin
 */
@Controller
@RequestMapping("/{repository}/search")
public class RepositorySearchController extends AbstractRepositoryRestController {

  public RepositorySearchController(Repositories repositories,
                                    RepositoryRestConfiguration config,
                                    DomainClassConverter domainClassConverter,
                                    ConversionService conversionService) {
    super(repositories, config, domainClassConverter, conversionService);
  }

  @RequestMapping(
      method = RequestMethod.GET,
      produces = {
          "application/json",
          "application/x-spring-data-compact+json"
      }
  )
  @ResponseBody
  public Resource<?> list(RepositoryRestRequest repoRequest) {
    List<Link> links = new ArrayList<Link>();
    links.addAll(queryMethodLinks(repoRequest.getBaseUri(),
                                  repoRequest.getPersistentEntity().getType()));
    return new Resource<Object>(Collections.emptyList(), links);
  }

  @RequestMapping(
      method = RequestMethod.GET,
      produces = {
          "application/javascript"
      }
  )
  @ResponseBody
  public JsonpResponse<?> listJsonp(RepositoryRestRequest repoRequest) {
    return jsonpWrapResponse(repoRequest, list(repoRequest), HttpStatus.OK);
  }

  @RequestMapping(
      value = "/{method}",
      method = RequestMethod.GET,
      produces = {
          "application/json",
          "application/x-spring-data-verbose+json"
      }
  )
  @ResponseBody
  public Resource<?> query(RepositoryRestRequest repoRequest,
                           @PathVariable String method)
      throws ResourceNotFoundException {
    RepositoryMethodInvoker repoMethodInvoker = repoRequest.getRepositoryMethodInvoker();
    if(repoMethodInvoker.getQueryMethods().isEmpty()) {
      throw new ResourceNotFoundException();
    }

    ResourceMapping repoMapping = repoRequest.getRepositoryResourceMapping();
    String methodName = repoMapping.getNameForPath(method);
    RepositoryMethod repoMethod = repoMethodInvoker.getQueryMethods().get(methodName);
    if(null == repoMethod) {
      throw new ResourceNotFoundException();
    }

    List<MethodParameter> methodParams = repoMethod.getParameters();
    Object[] paramValues = new Object[methodParams.size()];
    if(!methodParams.isEmpty()) {

    }
    for(int i = 0; i < paramValues.length; i++) {

    }

    return null;
  }

}
