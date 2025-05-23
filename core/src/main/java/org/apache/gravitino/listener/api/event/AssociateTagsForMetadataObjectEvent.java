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

package org.apache.gravitino.listener.api.event;

import org.apache.gravitino.MetadataObject;
import org.apache.gravitino.annotation.DeveloperApi;
import org.apache.gravitino.utils.MetadataObjectUtil;

/**
 * Represents an event that is triggered upon successfully associating tags with a metadata object.
 */
@DeveloperApi
public final class AssociateTagsForMetadataObjectEvent extends TagEvent {
  private final MetadataObject.Type objectType;
  private final String[] tagsToAdd;
  private final String[] tagsToRemove;
  private final String[] associatedTags;

  /**
   * Constructs an instance of {@code AssociateTagsForMetadataObjectEvent}.
   *
   * @param user The username of the individual who initiated the tag association.
   * @param metalake The metalake from which the tags were associated.
   * @param metadataObject The metadata object with which the tags were associated.
   * @param tagsToAdd The tags that were added.
   * @param tagsToRemove The tags that were removed.
   * @param associatedTags The resulting list of associated tags after the operation.
   */
  public AssociateTagsForMetadataObjectEvent(
      String user,
      String metalake,
      MetadataObject metadataObject,
      String[] tagsToAdd,
      String[] tagsToRemove,
      String[] associatedTags) {
    super(user, MetadataObjectUtil.toEntityIdent(metalake, metadataObject));
    this.objectType = metadataObject.type();
    this.tagsToAdd = tagsToAdd != null ? tagsToAdd.clone() : new String[0];
    this.tagsToRemove = tagsToRemove != null ? tagsToRemove.clone() : new String[0];
    this.associatedTags = associatedTags != null ? associatedTags.clone() : new String[0];
  }

  /**
   * Provides the type of metadata object associated with this event.
   *
   * @return The type of metadata object.
   */
  public MetadataObject.Type objectType() {
    return objectType;
  }

  /**
   * Provides the tags that were added in this operation.
   *
   * @return An array of tag names that were added.
   */
  public String[] tagsToAdd() {
    return tagsToAdd;
  }

  /**
   * Provides the tags that were removed in this operation.
   *
   * @return An array of tag names that were removed.
   */
  public String[] tagsToRemove() {
    return tagsToRemove;
  }

  /**
   * Provides the resulting list of associated tags after the operation.
   *
   * @return An array of tag names representing the associated tags.
   */
  public String[] associatedTags() {
    return associatedTags;
  }

  /**
   * Returns the type of operation.
   *
   * @return The operation type.
   */
  @Override
  public OperationType operationType() {
    return OperationType.ASSOCIATE_TAGS_FOR_METADATA_OBJECT;
  }
}
