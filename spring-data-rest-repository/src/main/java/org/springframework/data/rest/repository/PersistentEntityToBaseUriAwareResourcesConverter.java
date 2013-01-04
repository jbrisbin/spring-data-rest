package org.springframework.data.rest.repository;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.mapping.model.BeanWrapper;
import org.springframework.data.repository.support.Repositories;
import org.springframework.data.rest.config.RepositoryRestConfiguration;
import org.springframework.hateoas.Resource;

/**
 * @author Jon Brisbin
 */
public class PersistentEntityToBaseUriAwareResourcesConverter implements Converter<Iterable<?>, BaseUriAwareResources> {

  private final Repositories                repositories;
  private final ConversionService           conversionService;
  private final RepositoryRestConfiguration config;

  @Autowired
  public PersistentEntityToBaseUriAwareResourcesConverter(RepositoryRestConfiguration config,
                                                          Repositories repositories,
                                                          ConversionService conversionService) {
    this.config = config;
    this.repositories = repositories;
    this.conversionService = conversionService;
  }

  @SuppressWarnings({"unchecked"})
  @Override public BaseUriAwareResources convert(Iterable<?> entities) {
    List<Resource<?>> resources = new ArrayList<Resource<?>>();
    for(Object obj : entities) {
      if(null == obj) {
        resources.add(null);
        break;
      }

      PersistentEntity persistentEntity = repositories.getPersistentEntity(obj.getClass());
      if(null == persistentEntity) {
        resources.add(new BaseUriAwareResource<Object>(obj));
        break;
      }

      BeanWrapper bean = BeanWrapper.create(obj, conversionService);
      resources.add(new PersistentEntityResource<Object>(persistentEntity, obj));
    }
    return new BaseUriAwareResources(resources);
  }

}
