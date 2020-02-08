/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for
 * license information.
 */

package com.microsoft.azure.spring.data.cosmosdb.repository.integration;

import com.microsoft.azure.spring.data.cosmosdb.domain.Contact;
import com.microsoft.azure.spring.data.cosmosdb.domain.Course;
import com.microsoft.azure.spring.data.cosmosdb.exception.CosmosDBAccessException;
import com.microsoft.azure.spring.data.cosmosdb.repository.TestRepositoryConfig;
import com.microsoft.azure.spring.data.cosmosdb.repository.repository.ContactRepository;
import com.microsoft.azure.spring.data.cosmosdb.repository.repository.ReactiveCourseRepository;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestRepositoryConfig.class)
public class SingleResponseRepositoryIT {

    private static final Contact TEST_CONTACT = new Contact("testId", "faketitle");
    private static final Course TEST_COURSE = new Course("1", "Course 1", "Department 1");

    @Autowired
    private ContactRepository repository;

    @Autowired
    private ReactiveCourseRepository reactiveRepository;

    @Before
    public void setUp() {
        repository.save(TEST_CONTACT);
    }

    @After
    public void cleanup() {
        repository.deleteAll();
    }

    @Test
    public void testShouldFindSingleEntity() {
        final Contact contact = repository.findOneByTitle(TEST_CONTACT.getTitle());

        Assert.assertEquals(TEST_CONTACT, contact);
    }

    @Test
    public void testShouldFindSingleOptionalEntity() {
        final Optional<Contact> contact = repository.findOptionallyByTitle(TEST_CONTACT.getTitle());
        Assert.assertTrue(contact.isPresent());
        Assert.assertEquals(TEST_CONTACT, contact.get());

        Assert.assertFalse(repository.findOptionallyByTitle("not here").isPresent());
    }

    @Test(expected = CosmosDBAccessException.class)
    public void testShouldFailIfMultipleResultsReturned() {
        repository.save(new Contact("testId2", TEST_CONTACT.getTitle()));

        repository.findOneByTitle(TEST_CONTACT.getTitle());
    }

    @Test
    public void testShouldAllowListAndIterableResponses() {
        final List<Contact> contactList = repository.findByTitle(TEST_CONTACT.getTitle());
        Assert.assertEquals(TEST_CONTACT, contactList.get(0));
        Assert.assertEquals(1, contactList.size());

        final Iterator<Contact> contactIterator = repository.findByLogicId(TEST_CONTACT.getLogicId()).iterator();
        Assert.assertTrue(contactIterator.hasNext());
        Assert.assertEquals(TEST_CONTACT, contactIterator.next());
        Assert.assertFalse(contactIterator.hasNext());
    }

    @Test
    public void testShouldFindSingleEntityReactive() {
        final Mono<Course> save = reactiveRepository.save(TEST_COURSE);
        StepVerifier.create(save).expectNext(TEST_COURSE).verifyComplete();

        final Mono<Course> find = reactiveRepository.findByName(TEST_COURSE.getName());
        StepVerifier.create(find).expectNext(TEST_COURSE).expectComplete().verify();
    }

}
