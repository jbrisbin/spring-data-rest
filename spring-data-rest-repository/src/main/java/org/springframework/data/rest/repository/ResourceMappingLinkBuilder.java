package org.springframework.data.rest.repository;

import java.net.URI;

import org.springframework.data.mapping.PersistentProperty;
import org.springframework.data.rest.config.ResourceMapping;
import org.springframework.hateoas.Identifiable;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.LinkBuilder;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * @author Jon Brisbin
 */
public class ResourceMappingLinkBuilder implements LinkBuilder {

  private final URI                  baseUri;
  private final ResourceMapping      resourceMapping;
  private final UriComponentsBuilder builder;

  public ResourceMappingLinkBuilder(URI baseUri, ResourceMapping resourceMapping) {
    this.baseUri = baseUri;
    this.builder = UriComponentsBuilder.fromUri(baseUri);
    this.resourceMapping = resourceMapping;
  }

  @Override public LinkBuilder slash(Object object) {
    if(object instanceof PersistentProperty) {

    }
    return null;
  }

  @Override public LinkBuilder slash(Identifiable<?> identifiable) {
    return null;
  }

  @Override public URI toUri() {
    return null;
  }

  @Override public Link withRel(String rel) {
    return null;
  }

  @Override public Link withSelfRel() {
    return null;
  }

}
