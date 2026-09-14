package eu.xfsc.fc.core.dao.schemas;

/*-
 * ---license-start
 * fc-service-core
 * ---
 * Copyright (c) 2022 - 2026 Contributors to the Eclipse Foundation
 * ---
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 * ---license-end
 */

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.service.schemastore.SchemaRecord;

public interface SchemaDao {

	int getSchemaCount();
	Optional<SchemaRecord> select(String schemaId);
	Map<String, Collection<String>> selectSchemas();
	Map<String, Collection<String>> selectSchemasByTerm(String term);
	boolean insert(SchemaRecord schema);
	void update(String id, String content, Collection<String> terms) throws NotFoundException;
	String delete(String schemaId);
	int deleteAll();
	Optional<String> selectLatestContentByType(String typeName);

	/**
	 * Return all versions of the schema, ordered ascending by version number.
	 *
	 * @param schemaId the schema identifier
	 * @return list of schema records with version numbers, or empty list if not found
	 */
	List<SchemaRecord> selectVersions(String schemaId);

	/**
	 * Return a specific version of the schema.
	 *
	 * @param schemaId the schema identifier
	 * @param version  1-based version ordinal
	 * @return the schema record at that version, or empty if not found
	 */
	Optional<SchemaRecord> selectVersion(String schemaId, int version);

	/**
	 * Return the number of Envers revisions for a schema (i.e. version count).
	 *
	 * @param schemaId the schema identifier
	 * @return revision count, or 0 if schema not found
	 */
	int getVersionCount(String schemaId);

}
