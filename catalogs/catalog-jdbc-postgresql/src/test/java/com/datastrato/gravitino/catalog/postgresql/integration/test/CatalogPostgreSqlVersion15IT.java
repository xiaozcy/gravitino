/*
 *  Copyright 2024 Datastrato Pvt Ltd.
 *  This software is licensed under the Apache License version 2.
 */

package com.datastrato.gravitino.catalog.postgresql.integration.test;

import com.datastrato.gravitino.integration.test.container.PGImageName;
import org.junit.jupiter.api.Tag;

@Tag("gravitino-docker-test")
public class CatalogPostgreSqlVersion15IT extends CatalogPostgreSqlIT {
  public CatalogPostgreSqlVersion15IT() {
    postgreImageName = PGImageName.VERSION_15;
  }
}
