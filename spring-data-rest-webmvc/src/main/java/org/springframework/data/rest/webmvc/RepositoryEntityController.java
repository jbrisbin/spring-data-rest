package org.springframework.data.rest.webmvc;

import static org.springframework.data.rest.core.util.UriUtils.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.mapping.PersistentProperty;
import org.springframework.data.mapping.model.BeanWrapper;
import org.springframework.data.repository.support.DomainClassConverter;
import org.springframework.data.repository.support.Repositories;
import org.springframework.data.rest.config.RepositoryRestConfiguration;
import org.springframework.data.rest.config.ResourceMapping;
import org.springframework.data.rest.repository.PersistentEntityResource;
import org.springframework.data.rest.repository.context.AfterDeleteEvent;
import org.springframework.data.rest.repository.context.AfterSaveEvent;
import org.springframework.data.rest.repository.context.BeforeDeleteEvent;
import org.springframework.data.rest.repository.context.BeforeSaveEvent;
import org.springframework.data.rest.repository.invoke.RepositoryMethodInvoker;
import org.springframework.data.rest.repository.support.DomainObjectMerger;
import org.springframework.data.rest.webmvc.support.JsonpResponse;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.Resources;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * @author Jon Brisbin
 */
@Controller
@RequestMapping("/{repository}")
public class RepositoryEntityController extends AbstractRepositoryRestController {

  @Autowired
  private DomainObjectMerger domainObjectMerger;

  public RepositoryEntityController(Repositories repositories,
                                    RepositoryRestConfiguration config,
                                    DomainClassConverter domainClassConverter,
                                    ConversionService conversionService) {
    super(repositories, config, domainClassConverter, conversionService);
  }

  @SuppressWarnings({"unchecked"})
  @RequestMapping(
      method = RequestMethod.GET,
      produces = {
          "application/json",
          "application/x-spring-data-verbose+json"
      }
  )
  @ResponseBody
  public Resources<Resource<?>> listEntities(RepositoryRestRequest repoRequest)
      throws ResourceNotFoundException {
    List<Resource<?>> resources = new ArrayList<Resource<?>>();
    List<Link> links = new ArrayList<Link>();

    Iterable<?> results;
    RepositoryMethodInvoker repoMethodInvoker = repoRequest.getRepositoryMethodInvoker();
    boolean hasPagingParams = (null != repoRequest.getRequest().getParameter(config.getPageParamName()));
    boolean hasSortParams = (null != repoRequest.getRequest().getParameter(config.getSortParamName()));
    if(repoMethodInvoker.hasFindAllPageable() && hasPagingParams) {
      results = repoMethodInvoker.findAll(new PageRequest(repoRequest.getPagingAndSorting().getPageNumber(),
                                                          repoRequest.getPagingAndSorting().getPageSize(),
                                                          repoRequest.getPagingAndSorting().getSort()));
    } else if(repoMethodInvoker.hasFindAllSorted() && hasSortParams) {
      results = repoMethodInvoker.findAll(repoRequest.getPagingAndSorting().getSort());
    } else if(repoMethodInvoker.hasFindAll()) {
      results = repoMethodInvoker.findAll();
    } else {
      throw new ResourceNotFoundException();
    }

    for(Object o : results) {
      resources.add(new PersistentEntityResource<Object>(repoRequest.getPersistentEntity(),
                                                         o,
                                                         repoRequest.buildEntitySelfLink(o, conversionService))
                        .setBaseUri(repoRequest.getBaseUri()));
    }


    if(!repoMethodInvoker.getQueryMethods().isEmpty()) {
      ResourceMapping repoMapping = repoRequest.getRepositoryResourceMapping();
      links.add(new Link(buildUri(repoRequest.getBaseUri(), repoMapping.getPath(), "search").toString(),
                         repoMapping.getRel() + ".search"));
    }

    return new Resources<Resource<?>>(resources, links);
  }

  @RequestMapping(
      method = RequestMethod.GET,
      produces = {
          "application/javascript"
      }
  )
  @ResponseBody
  public JsonpResponse<? extends Resources<Resource<?>>> jsonpListEntities(RepositoryRestRequest repoRequest)
      throws ResourceNotFoundException {
    return jsonpWrapResponse(repoRequest, listEntities(repoRequest), HttpStatus.OK);
  }

  @SuppressWarnings({"unchecked"})
  @RequestMapping(
      method = RequestMethod.GET,
      produces = {
          "application/x-spring-data-compact+json"
      }
  )
  @ResponseBody
  public Resources<Resource<?>> listEntitiesCompact(RepositoryRestRequest repoRequest)
      throws ResourceNotFoundException {
    ResourceMapping repoMapping = repoRequest.getRepositoryResourceMapping();
    ResourceMapping entityMapping = repoRequest.getPersistentEntityResourceMapping();

    Resources<Resource<?>> resources = listEntities(repoRequest);
    List<Link> links = new ArrayList<Link>(resources.getLinks());

    for(Resource<?> resource : resources.getContent()) {
      PersistentEntityResource<?> persistentEntityResource = (PersistentEntityResource<?>)resource;
      links.add(resourceLink(repoRequest, persistentEntityResource));
    }

    return new Resources<Resource<?>>(EMPTY_RESOURCE_LIST, links);
  }

  @SuppressWarnings({"unchecked"})
  @RequestMapping(
      method = RequestMethod.POST,
      consumes = {
          "application/json"
      },
      produces = {
          "application/json"
      }
  )
  @ResponseBody
  public ResponseEntity<Resource<?>> createNewEntity(RepositoryRestRequest repoRequest,
                                                     PersistentEntityResource<?> incoming) {
    RepositoryMethodInvoker repoMethodInvoker = repoRequest.getRepositoryMethodInvoker();
    if(!repoMethodInvoker.hasSaveOne()) {
      throw new NoSuchMethodError();
    }

    applicationContext.publishEvent(new BeforeSaveEvent(incoming.getContent()));
    Object obj = repoMethodInvoker.save(incoming.getContent());
    applicationContext.publishEvent(new AfterSaveEvent(obj));

    Link selfLink = repoRequest.buildEntitySelfLink(obj, conversionService);
    HttpHeaders headers = new HttpHeaders();
    headers.setLocation(URI.create(selfLink.getHref()));

    return resourceResponse(headers,
                            new PersistentEntityResource<Object>(repoRequest.getPersistentEntity(),
                                                                 obj,
                                                                 selfLink)
                                .setBaseUri(repoRequest.getBaseUri()),
                            HttpStatus.CREATED);
  }

  @SuppressWarnings({"unchecked"})
  @RequestMapping(
      method = RequestMethod.POST,
      consumes = {
          "application/json"
      },
      produces = {
          "application/javascript"
      }
  )
  @ResponseBody
  public JsonpResponse<? extends Resource<?>> jsonpCreateNewEntity(RepositoryRestRequest repoRequest,
                                                                   PersistentEntityResource<?> incoming) {
    return jsonpWrapResponse(repoRequest, createNewEntity(repoRequest, incoming));
  }

  @SuppressWarnings({"unchecked"})
  @RequestMapping(
      value = "/{id}",
      method = RequestMethod.GET,
      produces = {
          "application/json"
      }
  )
  @ResponseBody
  public Resource<?> getSingleEntity(RepositoryRestRequest repoRequest,
                                     @PathVariable String id)
      throws ResourceNotFoundException {
    RepositoryMethodInvoker repoMethodInvoker = repoRequest.getRepositoryMethodInvoker();
    if(!repoMethodInvoker.hasFindOne()) {
      throw new ResourceNotFoundException();
    }

    Object domainObj = domainClassConverter.convert(id,
                                                    STRING_TYPE,
                                                    TypeDescriptor.valueOf(repoRequest.getPersistentEntity()
                                                                                      .getType()));
    if(null == domainObj) {
      throw new ResourceNotFoundException();
    }

    PersistentEntityResource per = PersistentEntityResource.wrap(repoRequest.getPersistentEntity(),
                                                                 domainObj,
                                                                 repoRequest.getBaseUri());
    per.add(repoRequest.buildEntitySelfLink(domainObj, conversionService));
    return per;
  }

  @SuppressWarnings({"unchecked"})
  @RequestMapping(
      value = "/{id}",
      method = RequestMethod.GET,
      produces = {
          "application/javascript"
      }
  )
  @ResponseBody
  public JsonpResponse<? extends Resource<?>> jsonpGetSingleEntity(RepositoryRestRequest repoRequest,
                                                                   @PathVariable String id)
      throws ResourceNotFoundException {
    return jsonpWrapResponse(repoRequest,
                             getSingleEntity(repoRequest, id),
                             HttpStatus.OK);
  }

  @SuppressWarnings({"unchecked"})
  @RequestMapping(
      value = "/{id}",
      method = RequestMethod.PUT,
      consumes = {
          "application/json"
      },
      produces = {
          "application/json"
      }
  )
  @ResponseBody
  public ResponseEntity<Resource<?>> updateEntity(RepositoryRestRequest repoRequest,
                                                  PersistentEntityResource<?> incoming,
                                                  @PathVariable String id)
      throws ResourceNotFoundException {
    RepositoryMethodInvoker repoMethodInvoker = repoRequest.getRepositoryMethodInvoker();
    if(!repoMethodInvoker.hasSaveOne() || !repoMethodInvoker.hasFindOne()) {
      throw new NoSuchMethodError();
    }

    Object domainObj = domainClassConverter.convert(id,
                                                    STRING_TYPE,
                                                    TypeDescriptor.valueOf(repoRequest.getPersistentEntity()
                                                                                      .getType()));
    if(null == domainObj) {
      return createNewEntity(repoRequest, incoming);
    }

    domainObjectMerger.merge(incoming.getContent(), domainObj);

    applicationContext.publishEvent(new BeforeSaveEvent(incoming.getContent()));
    Object obj = repoMethodInvoker.save(domainObj);
    applicationContext.publishEvent(new AfterSaveEvent(obj));

    PersistentEntityResource per = PersistentEntityResource.wrap(repoRequest.getPersistentEntity(),
                                                                 obj,
                                                                 repoRequest.getBaseUri());
    per.add(repoRequest.buildEntitySelfLink(obj, conversionService));
    return resourceResponse(null,
                            per,
                            HttpStatus.OK);
  }

  @SuppressWarnings({"unchecked"})
  @RequestMapping(
      value = "/{id}",
      method = RequestMethod.PUT,
      consumes = {
          "application/json"
      },
      produces = {
          "application/javascript"
      }
  )
  @ResponseBody
  public JsonpResponse<? extends Resource<?>> jsonpUpdateEntity(RepositoryRestRequest repoRequest,
                                                                PersistentEntityResource<?> incoming,
                                                                @PathVariable String id)
      throws ResourceNotFoundException {
    return jsonpWrapResponse(repoRequest, updateEntity(repoRequest, incoming, id));
  }

  @SuppressWarnings({"unchecked"})
  @RequestMapping(
      value = "/{id}",
      method = RequestMethod.DELETE
  )
  @ResponseBody
  public ResponseEntity<?> deleteEntity(RepositoryRestRequest repoRequest,
                                        @PathVariable String id)
      throws ResourceNotFoundException {
    RepositoryMethodInvoker repoMethodInvoker = repoRequest.getRepositoryMethodInvoker();
    if(!repoMethodInvoker.hasFindOne() &&
        !(repoMethodInvoker.hasDeleteOne() || repoMethodInvoker.hasDeleteOneById())) {
      throw new NoSuchMethodError();
    }

    Object domainObj = domainClassConverter.convert(id,
                                                    STRING_TYPE,
                                                    TypeDescriptor.valueOf(repoRequest.getPersistentEntity()
                                                                                      .getType()));
    if(null == domainObj) {
      throw new ResourceNotFoundException();
    }

    applicationContext.publishEvent(new BeforeDeleteEvent(domainObj));
    if(repoMethodInvoker.hasDeleteOne()) {
      repoMethodInvoker.delete(domainObj);
    } else if(repoMethodInvoker.hasDeleteOneById()) {
      Class<?> idType = repoRequest.getPersistentEntity().getIdProperty().getType();
      Object idVal = conversionService.convert(id, idType);
      repoMethodInvoker.delete(idVal);
    }
    applicationContext.publishEvent(new AfterDeleteEvent(domainObj));

    return new ResponseEntity<Object>(HttpStatus.NO_CONTENT);
  }

  @SuppressWarnings({"unchecked"})
  @RequestMapping(
      value = "{id}",
      method = RequestMethod.DELETE,
      produces = {
          "application/javascript"
      }
  )
  @ResponseBody
  public JsonpResponse<?> jsonpDeleteEntity(RepositoryRestRequest repoRequest,
                                            @PathVariable String id)
      throws ResourceNotFoundException {
    return jsonpWrapResponse(repoRequest, deleteEntity(repoRequest, id));
  }

  @SuppressWarnings({"unchecked"})
  @RequestMapping(
      value = "/{id}/{property}",
      method = RequestMethod.GET,
      produces = {
          "application/json",
          "application/x-spring-data-verbose+json"
      }
  )
  @ResponseBody
  public ResponseEntity<Resource<?>> listLinkedEntities(RepositoryRestRequest repoRequest,
                                                        @PathVariable String id,
                                                        @PathVariable String property)
      throws ResourceNotFoundException {
    RepositoryMethodInvoker repoMethodInvoker = repoRequest.getRepositoryMethodInvoker();
    if(!repoMethodInvoker.hasFindOne()) {
      throw new ResourceNotFoundException();
    }

    Object domainObj = domainClassConverter.convert(id,
                                                    STRING_TYPE,
                                                    TypeDescriptor.valueOf(repoRequest.getPersistentEntity()
                                                                                      .getType()));
    if(null == domainObj) {
      throw new ResourceNotFoundException();
    }

    String propertyName = repoRequest.getPersistentEntityResourceMapping().getNameForPath(property);
    PersistentProperty prop = repoRequest.getPersistentEntity().getPersistentProperty(propertyName);
    if(null == prop) {
      throw new ResourceNotFoundException();
    }

    BeanWrapper wrapper = BeanWrapper.create(domainObj, conversionService);
    Object propVal = wrapper.getProperty(prop);
    if(null == propVal) {
      throw new ResourceNotFoundException();
    }

    if(propVal instanceof Iterable) {
      List<Resource<?>> resources = new ArrayList<Resource<?>>();
      PersistentEntity entity = repositories.getPersistentEntity(prop.getComponentType());
      for(Object obj : ((Iterable)propVal)) {
        PersistentEntityResource per = PersistentEntityResource.wrap(entity, obj, repoRequest.getBaseUri());
        Link selfLink = repoRequest.buildEntitySelfLink(obj, conversionService);
        per.add(selfLink);
        resources.add(per);
      }

      return resourceResponse(null, new Resource<Object>(resources), HttpStatus.OK);
    } else {
      PersistentEntityResource per = PersistentEntityResource.wrap(repositories.getPersistentEntity(prop.getType()),
                                                                   propVal,
                                                                   repoRequest.getBaseUri());
      Link selfLink = repoRequest.buildEntitySelfLink(propVal, conversionService);
      per.add(selfLink);

      HttpHeaders headers = new HttpHeaders();
      headers.set("Content-Location", selfLink.getHref());

      return resourceResponse(headers, new Resource<Object>(per), HttpStatus.OK);
    }
  }

  @SuppressWarnings({"unchecked"})
  @RequestMapping(
      value = "/{id}/{property}",
      method = RequestMethod.GET,
      produces = {
          "application/x-spring-data-compact+json"
      }
  )
  @ResponseBody
  public ResponseEntity<Resource<?>> listLinkedEntitiesCompact(RepositoryRestRequest repoRequest,
                                                               @PathVariable String id,
                                                               @PathVariable String property)
      throws ResourceNotFoundException {
    ResponseEntity<Resource<?>> response = listLinkedEntities(repoRequest, id, property);
    if(response.getStatusCode() != HttpStatus.OK) {
      return response;
    }

    ResourceMapping repoMapping = repoRequest.getRepositoryResourceMapping();
    ResourceMapping entityMapping = repoRequest.getPersistentEntityResourceMapping();
    ResourceMapping propMapping = entityMapping.getResourceMappingFor(entityMapping.getNameForPath(property));
    String propRel = (null != propMapping ? propMapping.getRel() : property);

    Resource<?> resource = response.getBody();

    List<Link> links = new ArrayList<Link>();

    URI entityBaseUri = buildUri(repoRequest.getBaseUri(),
                                 repoMapping.getPath(),
                                 id,
                                 property);

    if(resource.getContent() instanceof Iterable) {
      for(Resource<?> res : (Iterable<Resource<?>>)resource.getContent()) {
        Link propLink = linkedResourceLink(res, entityBaseUri, propRel);
        links.add(propLink);
      }
    } else {
      links.add(new Link(entityBaseUri.toString(), propRel));
    }

    return resourceResponse(null, new Resource<Object>(EMPTY_RESOURCE_LIST, links), HttpStatus.OK);
  }

  private Link linkedResourceLink(Resource<?> resource,
                                  URI baseUri,
                                  String rel) {
    Link selfLink = resource.getLink("self");
    String objId = selfLink.getHref().substring(selfLink.getHref().lastIndexOf('/') + 1);
    return new Link(buildUri(baseUri, objId).toString(), rel);
  }

}
