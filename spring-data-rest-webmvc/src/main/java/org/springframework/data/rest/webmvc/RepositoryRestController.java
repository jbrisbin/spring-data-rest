package org.springframework.data.rest.webmvc;

import static java.lang.String.*;
import static org.springframework.data.rest.core.util.UriUtils.*;
import static org.springframework.data.rest.repository.support.ResourceMappingUtils.*;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.support.DomainClassConverter;
import org.springframework.data.rest.config.ResourceMapping;
import org.springframework.data.rest.repository.PersistentEntityResource;
import org.springframework.data.rest.repository.context.AfterSaveEvent;
import org.springframework.data.rest.repository.context.BeforeSaveEvent;
import org.springframework.data.rest.repository.invoke.MethodParameterConversionService;
import org.springframework.data.rest.repository.invoke.RepositoryMethod;
import org.springframework.data.rest.repository.invoke.RepositoryMethodInvoker;
import org.springframework.data.rest.repository.json.JsonSchema;
import org.springframework.data.rest.repository.json.PersistentEntityToJsonSchemaConverter;
import org.springframework.data.rest.repository.support.RepositoryInformationSupport;
import org.springframework.data.rest.webmvc.support.ConstraintViolationExceptionMessage;
import org.springframework.data.rest.webmvc.support.ExceptionMessage;
import org.springframework.data.rest.webmvc.support.JsonpResponse;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.Resources;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * @author Jon Brisbin
 */
public class RepositoryRestController
    extends RepositoryInformationSupport
    implements ApplicationContextAware,
               InitializingBean {

  private static final Logger                 LOG                 = LoggerFactory.getLogger(RepositoryRestController.class);
  private static final TypeDescriptor         STRING_TYPE         = TypeDescriptor.valueOf(String.class);
  private static final Iterable<Resource<?>>  EMPTY_RESOURCE_LIST = Collections.emptyList();
  private static final Resources<Resource<?>> EMPTY_RESOURCES     = new Resources<Resource<?>>(Collections.<Resource<?>>emptyList());

  private ApplicationContext                    applicationContext;
  @Autowired
  private DomainClassConverter                  domainClassConverter;
  @Autowired
  private ConversionService                     conversionService;
  @Autowired
  private PersistentEntityToJsonSchemaConverter jsonSchemaConverter;
  private MethodParameterConversionService      methodParameterConversionService;

  @Override public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    this.applicationContext = applicationContext;
  }

  @Override public void afterPropertiesSet() throws Exception {
    methodParameterConversionService = new MethodParameterConversionService(conversionService);
  }

  /**
   * List available {@link org.springframework.data.repository.CrudRepository}s that are being exported.
   *
   * @param baseUri
   *     The URI under which all URLs are considered relative.
   *
   * @return {@link Resources} with links to the available repositories.
   *
   * @throws IOException
   */
  @SuppressWarnings({"unchecked"})
  @RequestMapping(
      value = "/",
      method = RequestMethod.GET,
      produces = {
          "application/json",
          "application/x-spring-data-compact+json"
      }
  )
  @ResponseBody
  public Resources<?> listRepositories(URI baseUri) throws IOException {
    return new Resources(EMPTY_RESOURCE_LIST, getRepositoryLinks(baseUri));
  }

  @SuppressWarnings({"unchecked"})
  @RequestMapping(
      value = "/",
      method = RequestMethod.GET,
      produces = {
          "application/javascript"
      }
  )
  @ResponseBody
  public JsonpResponse listRepositoriesJsonp(RepositoryRestRequest repoRequest,
                                             URI baseUri) throws IOException {
    return jsonpWrapResponse(repoRequest, listRepositories(baseUri), HttpStatus.OK);
  }

  /**
   * List entities of a {@link org.springframework.data.repository.CrudRepository} by invoking
   * {@link org.springframework.data.repository.CrudRepository#findAll()} and applying any available paging parameters.
   *
   * @param repoRequest
   *     The incoming request.
   *
   * @return A {@link Resources} of the inlined entities.
   *
   * @throws IOException
   */
  @SuppressWarnings({"unchecked"})
  @RequestMapping(
      value = "/{repository}",
      method = RequestMethod.GET,
      produces = {
          "application/json",
          "application/x-spring-data-verbose+json"
      }
  )
  @ResponseBody
  public Resources<Resource<?>> listEntities(RepositoryRestRequest repoRequest) throws IOException {
    List<Resource<?>> resources = new ArrayList<Resource<?>>();
    List<Link> links = new ArrayList<Link>();

    Iterable<?> results = Collections.emptyList();
    RepositoryMethodInvoker repoMethodInvoker = repoRequest.getRepositoryMethodInvoker();
    if(repoMethodInvoker.hasFindAllPageable()) {
      results = repoMethodInvoker.findAll(new PageRequest(repoRequest.getPagingAndSorting().getPageNumber(),
                                                          repoRequest.getPagingAndSorting().getPageSize(),
                                                          repoRequest.getPagingAndSorting().getSort()));
    } else if(repoMethodInvoker.hasFindAllSorted()) {
      results = repoMethodInvoker.findAll(repoRequest.getPagingAndSorting().getSort());
    } else if(repoMethodInvoker.hasFindAll()) {
      results = repoMethodInvoker.findAll();
    }

    for(Object o : results) {
      resources.add(new PersistentEntityResource<Object>(repoRequest.getPersistentEntity(),
                                                         o,
                                                         repoRequest.buildEntitySelfLink(o, conversionService)));
    }

    maybeAddSearchLinks(repoRequest, links);

    return new Resources<Resource<?>>(resources, links);
  }

  /**
   * List entities in a compact style by providing only links to the entities rather than inling them into the
   * response.
   *
   * @param repoRequest
   *     The incoming request.
   *
   * @return A {@link Resources} of the entity self links.
   *
   * @throws IOException
   */
  @RequestMapping(
      value = "/{repository}",
      method = RequestMethod.GET,
      produces = {
          "application/x-spring-data-compact+json"
      }
  )
  @ResponseBody
  public Resources<Resource<?>> listEntitiesCompact(RepositoryRestRequest repoRequest) throws IOException {
    List<Link> links = new ArrayList<Link>();
    for(Resource<?> resource : listEntities(repoRequest).getContent()) {
      links.add(selfToIdLink(repoRequest, resource.getLink("self")));
    }
    maybeAddSearchLinks(repoRequest, links);

    return new Resources<Resource<?>>(EMPTY_RESOURCE_LIST, links);
  }

  /**
   * JSONP version of {@link #listEntities(RepositoryRestRequest)}.
   *
   * @param repoRequest
   *     The incoming request.
   *
   * @return The JSON result wrapped in a call to the requested Javascript function.
   *
   * @throws IOException
   */
  @SuppressWarnings({"unchecked"})
  @RequestMapping(
      value = "/{repository}",
      method = RequestMethod.GET,
      produces = {
          "application/javascript"
      }
  )
  @ResponseBody
  public JsonpResponse listEntitiesJsonp(RepositoryRestRequest repoRequest) throws IOException {
    return jsonpWrapResponse(repoRequest, listEntities(repoRequest), HttpStatus.OK);
  }

  @RequestMapping(
      value = "/{repository}/search",
      method = RequestMethod.GET,
      produces = {
          "application/json",
          "application/x-spring-data-compact+json"
      }
  )
  @ResponseBody
  public Resource<?> listSearchMethods(RepositoryRestRequest repoRequest) {
    final Resource<?> searchMethods = new Resource<Object>(Collections.emptyList());
    List<Link> methodLinks = new ArrayList<Link>();
    maybeAddSearchLinks(repoRequest, methodLinks);
    searchMethods.add(methodLinks);
    return searchMethods;
  }

  @RequestMapping(
      value = "/{repository}/search",
      method = RequestMethod.GET,
      produces = {
          "application/javascript"
      }
  )
  @ResponseBody
  public JsonpResponse listSearchMethodsJsonp(RepositoryRestRequest repoRequest) {
    return jsonpWrapResponse(repoRequest, listSearchMethods(repoRequest), HttpStatus.OK);
  }

  @RequestMapping(
      value = "/{repository}",
      method = RequestMethod.GET,
      produces = {
          "application/schema+json"
      }
  )
  @ResponseBody
  public Resource<?> generateJsonSchema(RepositoryRestRequest repoRequest) {
    JsonSchema jsonSchema = jsonSchemaConverter.convert(repoRequest.getPersistentEntity().getType());
    jsonSchema.add(repoRequest.getRepositoryLink());
    return jsonSchema;
  }

  @SuppressWarnings({"unchecked"})
  @RequestMapping(
      value = "/{repository}/search/{queryMethod}",
      method = RequestMethod.GET,
      produces = {
          "application/json",
          "application/x-spring-data-verbose+json"
      }
  )
  @ResponseBody
  public Resources<Resource<?>> executeSearchMethod(RepositoryRestRequest repoRequest,
                                                    @PathVariable String queryMethod) throws ResourceNotFoundException {
    RepositoryMethodInvoker repoMethodInvoker = repoRequest.getRepositoryMethodInvoker();
    RepositoryMethod repoMethod = repoMethodInvoker.getRepositoryMethod(queryMethod);
    // Try to find it via external resource mapping
    if(null == repoMethod) {
      String name = repoRequest.getRepositoryResourceMapping()
                               .getNameForPath(queryMethod);
      repoMethod = repoMethodInvoker.getRepositoryMethod(name);
    }
    // Try to find it via annotations
    if(null == repoMethod) {
      for(RepositoryMethod rm : repoMethodInvoker.getQueryMethods().values()) {
        String name = findPath(rm.getMethod());
        if(queryMethod.equals(name)) {
          repoMethod = rm;
        }
      }
    }
    // No such method exists on this repository
    if(null == repoMethod) {
      throw new ResourceNotFoundException(
          "Query method " + queryMethod + " not found on repository " + repoRequest.getRepositoryInformation()
                                                                                   .getRepositoryInterface()
                                                                                   .getName());
    }

    List<Object> paramValues = new ArrayList<Object>();
    int idx = 0;
    for(MethodParameter param : repoMethod.getParameters()) {
      if(Pageable.class.isAssignableFrom(param.getParameterType())) {
        paramValues.add(new PageRequest(repoRequest.getPagingAndSorting().getPageNumber(),
                                        repoRequest.getPagingAndSorting().getPageSize(),
                                        repoRequest.getPagingAndSorting().getSort()));
      } else if(Sort.class.isAssignableFrom(param.getParameterType())) {
        paramValues.add(repoRequest.getPagingAndSorting().getSort());
      } else {
        String paramName = repoMethod.getParameterNames().get(idx);
        String[] paramVals = repoRequest.getRequest().getParameterValues(paramName);
        paramValues.add(methodParameterConversionService.convert(paramVals, param));
      }
      idx++;
    }

    Resources<Resource<?>> resources = EMPTY_RESOURCES;
    List<Link> links = new ArrayList<Link>();
    Object obj = repoMethodInvoker.invokeQueryMethod(repoMethod, paramValues.toArray());
    if(obj instanceof Page) {
      Page page = (Page)obj;
      if(page.hasPreviousPage()) {
        repoRequest.addPrevLink(page, links);
      }
      if(page.hasNextPage()) {
        repoRequest.addNextLink(page, links);
      }
      if(page.hasContent()) {
        resources = entitiesToResources(repoRequest, page, links);
      }
    } else if(obj instanceof Iterable) {
      resources = entitiesToResources(repoRequest, (Iterable<?>)obj, links);
    } else {
      resources = entitiesToResources(repoRequest, Collections.singletonList(obj), links);
    }

    return resources;
  }

  @RequestMapping(
      value = "/{repository}/search/{queryMethod}",
      method = RequestMethod.GET,
      produces = {
          "application/x-spring-data-compact+json"
      }
  )
  @ResponseBody
  public Resources<Resource<?>> executeSearchMethodCompact(final RepositoryRestRequest repoRequest,
                                                           @PathVariable String queryMethod)
      throws ResourceNotFoundException {
    Resources<Resource<?>> resources = new Resources<Resource<?>>(EMPTY_RESOURCE_LIST);
    for(Resource<?> resource : executeSearchMethod(repoRequest, queryMethod).getContent()) {
      resources.add(selfToIdLink(repoRequest, resource.getLink("self")));
    }

    return resources;
  }

  @RequestMapping(
      value = "/{repository}/search/{queryMethod}",
      method = RequestMethod.GET,
      produces = {
          "application/javascript"
      }
  )
  @ResponseBody
  public JsonpResponse executeSearchMethodJsonp(final RepositoryRestRequest repoRequest,
                                                @PathVariable String queryMethod) throws ResourceNotFoundException {
    return jsonpWrapResponse(repoRequest, executeSearchMethod(repoRequest, queryMethod), HttpStatus.OK);
  }

  /**
   * Retrieve a specific entity.
   *
   * @param repoRequest
   *     The incoming request.
   * @param id
   *     The ID of the entity to load.
   *
   * @return A {@link Resource} of the entity.
   */
  @SuppressWarnings({"unchecked"})
  @RequestMapping(
      value = "/{repository}/{id}",
      method = RequestMethod.GET,
      produces = {
          "application/json"
      }
  )
  @ResponseBody
  public Resource<?> getEntity(RepositoryRestRequest repoRequest,
                               @PathVariable String id) throws ResourceNotFoundException {
    TypeDescriptor domainType = TypeDescriptor.valueOf(repoRequest.getPersistentEntity().getType());
    Object entity = domainClassConverter.convert(id, STRING_TYPE, domainType);
    if(null == entity) {
      throw new ResourceNotFoundException("Resource not found");
    }

    Link selfLink = repoRequest.buildEntitySelfLink(entity, conversionService);
    return new PersistentEntityResource<Object>(repoRequest.getPersistentEntity(),
                                                entity,
                                                selfLink);
  }

  @SuppressWarnings({"unchecked"})
  @RequestMapping(
      value = "/{repository}/{id}",
      method = RequestMethod.GET,
      produces = {
          "application/javascript"
      }
  )
  @ResponseBody
  public JsonpResponse getEntityJsonp(RepositoryRestRequest repoRequest,
                                      @PathVariable String id) throws ResourceNotFoundException {
    return jsonpWrapResponse(repoRequest, getEntity(repoRequest, id), HttpStatus.OK);
  }

  @SuppressWarnings({"unchecked"})
  @RequestMapping(
      value = "/{repository}",
      method = RequestMethod.POST,
      consumes = {
          "application/json"
      },
      produces = {
          "application/json"
      }
  )
  @ResponseBody
  public ResponseEntity<Resource<?>> createNewEntity(final RepositoryRestRequest repoRequest,
                                                     final PersistentEntityResource<?> incoming) throws Throwable {

    RepositoryMethodInvoker repoMethodInvoker = repoRequest.getRepositoryMethodInvoker();
    if(!repoMethodInvoker.hasSaveOne()) {
      throw new ResourceNotFoundException();
    }

    applicationContext.publishEvent(new BeforeSaveEvent(incoming.getContent()));
    Object obj = repoMethodInvoker.save(incoming.getContent());
    applicationContext.publishEvent(new AfterSaveEvent(obj));

    Link selfLink = repoRequest.buildEntitySelfLink(obj, conversionService);
    HttpHeaders headers = new HttpHeaders();
    headers.setLocation(URI.create(selfLink.getHref()));

    Resource<?> resource = new PersistentEntityResource<Object>(repoRequest.getPersistentEntity(),
                                                                obj,
                                                                selfLink);
    return resourceResponse(headers, resource, HttpStatus.CREATED);
  }

  @ExceptionHandler({
                        NullPointerException.class
                    })
  @ResponseBody
  public ResponseEntity<?> handleNPE(NullPointerException npe) {
    return errorResponse(npe, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @ExceptionHandler({
                        ResourceNotFoundException.class,
                        NoSuchMethodError.class
                    })
  @ResponseBody
  public ResponseEntity<?> handleNotFound() {
    return notFound();
  }

  @ExceptionHandler({
                        HttpMessageNotReadableException.class,
                        HttpMessageNotWritableException.class
                    })
  @ResponseBody
  public ResponseEntity<ExceptionMessage> handleNotReadable(HttpMessageNotReadableException e) {
    return badRequest(e);
  }

  /**
   * Handle failures commonly thrown from code tries to read incoming data and convert or cast it to the right type.
   *
   * @param t
   *
   * @return
   *
   * @throws IOException
   */
  @ExceptionHandler({
                        InvocationTargetException.class,
                        IllegalArgumentException.class,
                        ClassCastException.class,
                        ConversionFailedException.class
                    })
  @ResponseBody
  public ResponseEntity<ExceptionMessage> handleMiscFailures(Throwable t) {
    return badRequest(t);
  }

  @ExceptionHandler({
                        ConstraintViolationException.class
                    })
  @ResponseBody
  public ResponseEntity handleConstraintViolationException(ConstraintViolationException cve) {
    return response(null, new ConstraintViolationExceptionMessage(cve), HttpStatus.CONFLICT);
  }


  /**
   * Send a 409 Conflict in case of concurrent modification.
   *
   * @param ex
   *
   * @return
   */
  @SuppressWarnings({"unchecked"})
  @ExceptionHandler({
                        OptimisticLockingFailureException.class,
                        DataIntegrityViolationException.class
                    })
  @ResponseBody
  public ResponseEntity handleConflict(Exception ex) {
    return errorResponse(null, ex, HttpStatus.CONFLICT);
  }

  private <T> ResponseEntity<T> notFound() {
    return notFound(null, null);
  }

  private <T> ResponseEntity<T> notFound(HttpHeaders headers, T body) {
    return response(headers, body, HttpStatus.NOT_FOUND);
  }

  private <T extends Throwable> ResponseEntity<ExceptionMessage> badRequest(T throwable) {
    return badRequest(null, throwable);
  }

  private <T extends Throwable> ResponseEntity<ExceptionMessage> badRequest(HttpHeaders headers, T throwable) {
    return errorResponse(headers, throwable, HttpStatus.BAD_REQUEST);
  }

  public <T extends Throwable> ResponseEntity<ExceptionMessage> internalServerError(T throwable) {
    return internalServerError(null, throwable);
  }

  public <T extends Throwable> ResponseEntity<ExceptionMessage> internalServerError(HttpHeaders headers, T throwable) {
    return errorResponse(headers, throwable, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  public <T extends Throwable> ResponseEntity<ExceptionMessage> errorResponse(T throwable,
                                                                              HttpStatus status) {
    return errorResponse(null, throwable, status);
  }

  public <T extends Throwable> ResponseEntity<ExceptionMessage> errorResponse(HttpHeaders headers,
                                                                              T throwable,
                                                                              HttpStatus status) {
    LOG.error(throwable.getMessage(), throwable);
    return response(headers, new ExceptionMessage(throwable), status);
  }

  public <T> ResponseEntity<T> response(HttpHeaders headers, T body, HttpStatus status) {
    HttpHeaders hdrs = new HttpHeaders();
    if(null != headers) {
      hdrs.putAll(headers);
    }
    return new ResponseEntity<T>(body, hdrs, status);
  }

  public <R extends Resource<?>> ResponseEntity<Resource<?>> resourceResponse(HttpHeaders headers,
                                                                              R resource,
                                                                              HttpStatus status) {
    HttpHeaders hdrs = new HttpHeaders();
    if(null != headers) {
      hdrs.putAll(headers);
    }
    return new ResponseEntity<Resource<?>>(resource, hdrs, status);
  }

  private <T> JsonpResponse<T> jsonpWrapResponse(RepositoryRestRequest repoRequest,
                                                 T response,
                                                 HttpStatus status) {
    String callback = repoRequest.getRequest().getParameter(config.getJsonpParamName());
    String errback = repoRequest.getRequest().getParameter(config.getJsonpOnErrParamName());
    return new JsonpResponse<T>(new ResponseEntity<T>(response, status),
                                (null != callback ? callback : config.getJsonpParamName()),
                                (null != errback ? errback : config.getJsonpOnErrParamName()));
  }

  private void maybeAddSearchLinks(RepositoryRestRequest repoRequest,
                                   List<Link> resourceLinks) {
    List<Link> methodLinks = getQueryMethodLinks(repoRequest);
    if(methodLinks.size() > 0) {
      resourceLinks.add(new Link(buildUri(repoRequest.getBaseUri(),
                                          repoRequest.getRepositoryResourceMapping().getPath(),
                                          "search").toString(),
                                 format("%s.search",
                                        repoRequest.getRepositoryResourceMapping().getRel())));
      resourceLinks.addAll(methodLinks);
    }
  }

  private List<Link> getRepositoryLinks(URI baseUri) {
    List<Link> links = new ArrayList<Link>();
    for(Class<?> domainType : repositories) {
      RepositoryInformation repoInfo = findRepositoryInfoFor(domainType);
      ResourceMapping mapping = getResourceMapping(config, repoInfo);
      if(!mapping.isExported()) {
        return null;
      }

      String href = buildUri(baseUri, mapping.getPath()).toString();
      links.add(new Link(href, mapping.getRel()));
    }
    return links;
  }

  private List<Link> getQueryMethodLinks(RepositoryRestRequest req) {
    List<RepositoryMethod> methods = repositoryMethods.get(req.getRepositoryInformation().getRepositoryInterface());
    if(null == methods) {
      return Collections.emptyList();
    }

    List<Link> links = new ArrayList<Link>();
    for(RepositoryMethod repoMethod : methods) {
      if(!req.getRepositoryInformation().isQueryMethod(repoMethod.getMethod())) {
        continue;
      }
      String name = repoMethod.getMethod().getName();
      ResourceMapping methodMapping = req.getRepositoryResourceMapping().getResourceMappingFor(name);
      String path = findPath(repoMethod.getMethod());
      String rel = format("%s.%s",
                          req.getRepositoryResourceMapping().getRel(),
                          findRel(repoMethod.getMethod()));
      if(null != methodMapping) {
        if(null != methodMapping.getPath()) {
          path = methodMapping.getPath();
        }
        if(null != methodMapping.getRel()) {
          rel = methodMapping.getRel();
        }
      }
      links.add(new Link(buildUri(req.getBaseUri(),
                                  req.getRepositoryResourceMapping().getPath(),
                                  "search",
                                  path).toString(),
                         rel));
    }
    return links;
  }

  @SuppressWarnings({"unchecked"})
  private Resources<Resource<?>> entitiesToResources(RepositoryRestRequest repoRequest,
                                                     Iterable<?> entities,
                                                     List<Link> links) {
    List<Resource<?>> resources = new ArrayList<Resource<?>>();
    for(Object obj : entities) {
      resources.add(new PersistentEntityResource<Object>(
          repoRequest.getPersistentEntity(),
          obj,
          repoRequest.buildEntitySelfLink(obj, conversionService)));
    }
    return new Resources<Resource<?>>(resources, links);
  }

  private Link selfToIdLink(RepositoryRestRequest repoRequest, Link selfLink) {
    return new Link(selfLink.getHref(), format("%s.%s",
                                               repoRequest.getRepositoryResourceMapping().getRel(),
                                               repoRequest.getPersistentEntityResourceMapping().getRel()));
  }

}
