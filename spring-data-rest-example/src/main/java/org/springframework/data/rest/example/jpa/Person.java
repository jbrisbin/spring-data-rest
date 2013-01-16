package org.springframework.data.rest.example.jpa;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.PrePersist;
import javax.validation.constraints.NotNull;

import org.springframework.data.rest.repository.annotation.Description;

/**
 * An entity that represents a person.
 *
 * @author Jon Brisbin
 */
@Entity
public class Person {

  @Id @GeneratedValue private Long   id;
  @Description("A person's first name")
  private                     String firstName;
  @Description("A person's last name")
  @NotNull private            String lastName;
  @Description("A person's siblings")
  @ManyToMany private List<Person> siblings = Collections.emptyList();
  @ManyToOne private Person father;
  @Description("Timestamp this person object was created")
  private            Date   created;

  public Person() {
  }

  public Person(String firstName, String lastName) {
    this.firstName = firstName;
    this.lastName = lastName;
  }

  public Long getId() {
    return id;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public Person addSibling(Person p) {
    if(siblings == Collections.EMPTY_LIST) {
      siblings = new ArrayList<Person>();
    }
    siblings.add(p);
    return this;
  }

  public List<Person> getSiblings() {
    return siblings;
  }

  public void setSiblings(List<Person> siblings) {
    this.siblings = siblings;
  }

  public Person getFather() {
    return father;
  }

  public void setFather(Person father) {
    this.father = father;
  }

  public Date getCreated() {
    return created;
  }

  @PrePersist
  private void prePersist() {
    this.created = Calendar.getInstance().getTime();
  }

}