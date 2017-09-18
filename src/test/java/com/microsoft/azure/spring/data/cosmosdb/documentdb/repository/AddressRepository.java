/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for
 * license information.
 */
package com.microsoft.azure.spring.data.cosmosdb.documentdb.repository;

import com.microsoft.azure.spring.data.cosmosdb.documentdb.domain.Address;
import org.springframework.stereotype.Repository;

@Repository
public interface AddressRepository extends DocumentDbRepository<Address, String> {
}
