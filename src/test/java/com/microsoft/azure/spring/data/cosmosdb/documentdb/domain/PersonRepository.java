/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for
 * license information.
 */

package com.microsoft.azure.spring.data.cosmosdb.documentdb.domain;

import com.microsoft.azure.spring.data.cosmosdb.documentdb.repository.DocumentDbRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PersonRepository extends DocumentDbRepository<Person, String> {
}
