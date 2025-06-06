/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.gravitino.catalog;

import static org.apache.gravitino.Entity.SYSTEM_CATALOG_RESERVED_NAME;

import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import org.apache.gravitino.Catalog;
import org.apache.gravitino.CatalogChange;
import org.apache.gravitino.MetadataObjects;
import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.Namespace;
import org.apache.gravitino.exceptions.CatalogAlreadyExistsException;
import org.apache.gravitino.exceptions.CatalogInUseException;
import org.apache.gravitino.exceptions.NoSuchCatalogException;
import org.apache.gravitino.exceptions.NoSuchMetalakeException;
import org.apache.gravitino.exceptions.NonEmptyEntityException;

public class CatalogNormalizeDispatcher implements CatalogDispatcher {
  private static final Set<String> RESERVED_WORDS =
      ImmutableSet.of(MetadataObjects.METADATA_OBJECT_RESERVED_NAME, SYSTEM_CATALOG_RESERVED_NAME);
  /**
   * Regular expression explanation:
   *
   * <p>^\w - Starts with a letter, digit, or underscore
   *
   * <p>[\w]{0,63} - Followed by 0 to 63 characters (making the total length at most 64) of letters
   * (both cases), digits, underscores
   *
   * <p>$ - End of the string
   */
  private static final String CATALOG_NAME_PATTERN = "^\\w[\\w-]{0,63}$";

  private final CatalogDispatcher dispatcher;

  public CatalogNormalizeDispatcher(CatalogDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  @Override
  public NameIdentifier[] listCatalogs(Namespace namespace) throws NoSuchMetalakeException {
    return dispatcher.listCatalogs(namespace);
  }

  @Override
  public Catalog[] listCatalogsInfo(Namespace namespace) throws NoSuchMetalakeException {
    return dispatcher.listCatalogsInfo(namespace);
  }

  @Override
  public Catalog loadCatalog(NameIdentifier ident) throws NoSuchCatalogException {
    return dispatcher.loadCatalog(ident);
  }

  @Override
  public boolean catalogExists(NameIdentifier ident) {
    return dispatcher.catalogExists(ident);
  }

  @Override
  public Catalog createCatalog(
      NameIdentifier ident,
      Catalog.Type type,
      String provider,
      String comment,
      Map<String, String> properties)
      throws NoSuchMetalakeException, CatalogAlreadyExistsException {
    validateCatalogName(ident.name());
    return dispatcher.createCatalog(ident, type, provider, comment, properties);
  }

  @Override
  public Catalog alterCatalog(NameIdentifier ident, CatalogChange... changes)
      throws NoSuchCatalogException, IllegalArgumentException {
    Arrays.stream(changes)
        .forEach(
            c -> {
              if (c instanceof CatalogChange.RenameCatalog) {
                validateCatalogName(((CatalogChange.RenameCatalog) c).getNewName());
              }
            });
    return dispatcher.alterCatalog(ident, changes);
  }

  @Override
  public boolean dropCatalog(NameIdentifier ident) {
    return dispatcher.dropCatalog(ident);
  }

  @Override
  public boolean dropCatalog(NameIdentifier ident, boolean force)
      throws NonEmptyEntityException, CatalogInUseException {
    return dispatcher.dropCatalog(ident, force);
  }

  @Override
  public void testConnection(
      NameIdentifier ident,
      Catalog.Type type,
      String provider,
      String comment,
      Map<String, String> properties)
      throws Exception {
    validateCatalogName(ident.name());
    dispatcher.testConnection(ident, type, provider, comment, properties);
  }

  @Override
  public void enableCatalog(NameIdentifier ident) throws NoSuchCatalogException {
    dispatcher.enableCatalog(ident);
  }

  @Override
  public void disableCatalog(NameIdentifier ident) throws NoSuchCatalogException {
    dispatcher.disableCatalog(ident);
  }

  private void validateCatalogName(String name) throws IllegalArgumentException {
    if (RESERVED_WORDS.contains(name.toLowerCase())) {
      throw new IllegalArgumentException("The catalog name '" + name + "' is reserved.");
    }

    if (!name.matches(CATALOG_NAME_PATTERN)) {
      throw new IllegalArgumentException("The catalog name '" + name + "' is illegal.");
    }
  }
}
