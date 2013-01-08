package org.springframework.data.rest.webmvc;

import static org.springframework.data.rest.repository.support.ResourceMappingUtils.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.core.MethodParameter;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.repository.support.DomainClassConverter;
import org.springframework.data.repository.support.Repositories;
import org.springframework.data.rest.config.RepositoryRestConfiguration;
import org.springframework.data.rest.config.ResourceMapping;
import org.springframework.data.rest.repository.BaseUriAwareResource;
import org.springframework.data.rest.repository.PersistentEntityResource;
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

  @SuppressWarnings({"unchecked"})
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
      for(RepositoryMethod queryMethod : repoMethodInvoker.getQueryMethods().values()) {
        String path = findPath(queryMethod.getMethod());
        if(path.equals(method)) {
          repoMethod = queryMethod;
          break;
        }
      }
      if(null == repoMethod) {
        throw new ResourceNotFoundException();
      }
    }

    List<MethodParameter> methodParams = repoMethod.getParameters();
    Object[] paramValues = new Object[methodParams.size()];
    if(!methodParams.isEmpty()) {
      for(int i = 0; i < paramValues.length; i++) {
        MethodParameter param = methodParams.get(i);
        if(Pageable.class.isAssignableFrom(param.getParameterType())) {
          paramValues[i] = new PageRequest(repoRequest.getPagingAndSorting().getPageNumber(),
                                           repoRequest.getPagingAndSorting().getPageSize(),
                                           repoRequest.getPagingAndSorting().getSort());
        } else if(Sort.class.isAssignableFrom(param.getParameterType())) {
          paramValues[i] = repoRequest.getPagingAndSorting().getSort();
        } else {
          String paramName = repoMethod.getParameterNames().get(i);
          String[] queryParamVals = repoRequest.getRequest().getParameterValues(paramName);
          paramValues[i] = methodParameterConversionService.convert(queryParamVals, param);
        }
      }
    }

    BaseUriAwareResource resources;
    List<Link> links = new ArrayList<Link>();
    Object result = repoMethodInvoker.invokeQueryMethod(repoMethod, paramValues);
    if(result instanceof Page) {
      Page page = (Page)result;
      if(page.hasPreviousPage()) {
        repoRequest.addPrevLink(page, links);
      }
      if(page.hasNextPage()) {
        repoRequest.addNextLink(page, links);
      }
      if(page.hasContent()) {
        resources = entitiesToResource(repoRequest, page.getContent());
      } else {
        resources = new BaseUriAwareResource(EMPTY_RESOURCE_LIST);
      }
    } else if(result instanceof Iterable) {
      resources = entitiesToResource(repoRequest, (Iterable)result);
    } else if(null == result) {
      resources = new BaseUriAwareResource(EMPTY_RESOURCE_LIST);
    } else {
      Link selfLink = repoRequest.buildEntitySelfLink(result, conversionService);
      resources = new BaseUriAwareResource(new PersistentEntityResource<Object>(repoRequest.getPersistentEntity(),
                                                                                result,
                                                                                selfLink)
                                               .setBaseUri(repoRequest.getBaseUri()));
    }
    resources.setBaseUri(repoRequest.getBaseUri())
             .add(links);

    return resources;
  }

  @RequestMapping(
      value = "/{method}",
      method = RequestMethod.GET,
      produces = {
          "application/x-spring-data-compact+json"
      }
  )
  @ResponseBody
  public Resource<?> queryCompact(RepositoryRestRequest repoRequest,
                                  @PathVariable String method)
      throws ResourceNotFoundException {
    List<Link> links = new ArrayList<Link>();

    Resource<?> resource = query(repoRequest, method);
    links.addAll(resource.getLinks());

    if(resource.getContent() instanceof Iterable) {
      Iterable iter = (Iterable)resource.getContent();
      for(Object obj : iter) {
        if(null != obj && obj instanceof Resource) {
          Resource res = (Resource)obj;
          links.add(resourceLink(repoRequest, res));
        }
      }
    } else if(resource.getContent() instanceof Resource) {
      Resource res = (Resource)resource.getContent();
      links.add(resourceLink(repoRequest, res));
    }

    return new Resource<Object>(EMPTY_RESOURCE_LIST, links);
  }

  private Link resourceLink(RepositoryRestRequest repoRequest, Resource resource) {
    ResourceMapping repoMapping = repoRequest.getRepositoryResourceMapping();
    ResourceMapping entityMapping = repoRequest.getPersistentEntityResourceMapping();

    Link selfLink = resource.getLink("self");
    String rel = repoMapping.getRel() + "." + entityMapping.getRel();
    return new Link(selfLink.getHref(), rel);
  }

  @SuppressWarnings({"unchecked"})
  private BaseUriAwareResource entitiesToResource(RepositoryRestRequest repoRequest, Iterable entities) {
    List<Resource<?>> resources = new ArrayList<Resource<?>>();
    for(Object obj : entities) {
      if(null == obj) {
        resources.add(null);
        break;
      }

      PersistentEntity persistentEntity = repositories.getPersistentEntity(obj.getClass());
      if(null == persistentEntity) {
        resources.add(new BaseUriAwareResource<Object>(obj)
                          .setBaseUri(repoRequest.getBaseUri()));
        continue;
      }

      Link selfLink = repoRequest.buildEntitySelfLink(obj, conversionService);
      resources.add(new PersistentEntityResource<Object>(persistentEntity, obj, selfLink)
                        .setBaseUri(repoRequest.getBaseUri()));
    }
    return new BaseUriAwareResource(resources)
        .setBaseUri(repoRequest.getBaseUri());
  }

}
