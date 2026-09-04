package eu.xfsc.fc.server.helper;

/*-
 * ---license-start
 * fc-service-server
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileReaderHelper {
    public static String getMockFileDataAsString(String filename) throws IOException {
        Path resourceDirectory = Paths.get("src", "test", "resources", "mock-data");
        String absolutePath = resourceDirectory.toFile().getAbsolutePath();
        return new String(Files.readAllBytes(Paths.get(absolutePath + "/" + filename )));
    }
}
