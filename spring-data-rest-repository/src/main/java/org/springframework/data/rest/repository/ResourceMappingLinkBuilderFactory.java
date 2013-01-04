package org.springframework.data.rest.repository;

import static org.springframework.data.rest.repository.support.ResourceMappingUtils.getResourceMapping;

import java.net.URI;

import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.support.Repositories;
import org.springframework.data.rest.config.RepositoryRestConfiguration;
import org.springframework.data.rest.config.ResourceMapping;
import org.springframework.hateoas.LinkBuilderFactory;

/**
 * @author Jon Brisbin
 */
public class ResourceMappingLinkBuilderFactory implements LinkBuilderFactory<ResourceMappingLinkBuilder> {

  private final URI                         baseUri;
  private final Repositories                repositories;
  private final RepositoryRestConfiguration config;

  public ResourceMappingLinkBuilderFactory(URI baseUri,
                                           Repositories repositories,
                                           RepositoryRestConfiguration config) {
    this.baseUri = baseUri;
    this.repositories = repositories;
    this.config = config;
  }

  @Override public ResourceMappingLinkBuilder linkTo(Class<?> target) {
    PersistentEntity persistentEntity = repositories.getPersistentEntity(target);
    if(null != persistentEntity) {
      ResourceMapping mapping = getResourceMapping(config, persistentEntity);
    }
    RepositoryInformation repoInfo = repositories.getRepositoryInformationFor(target);

    return null;
  }

  @Override public ResourceMappingLinkBuilder linkTo(Class<?> target, Object... parameters) {
    return null;
  }

}
