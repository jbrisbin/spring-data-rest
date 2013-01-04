package org.springframework.data.rest.example.jpa;

import java.util.Arrays;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Jon Brisbin
 */
@Component
public class PersonLoader implements InitializingBean {

  @Autowired
  PersonRepository people;

  @Override public void afterPropertiesSet() throws Exception {
    people.save(Arrays.asList(new Person("John", "Doe"),
                              new Person("Jane", "Doe"),
                              new Person("Billy Bob", "Thornton")));
  }

}
