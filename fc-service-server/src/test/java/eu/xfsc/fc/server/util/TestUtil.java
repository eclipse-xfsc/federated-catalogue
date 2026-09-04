package eu.xfsc.fc.server.util;

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

import java.io.File;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorFile;

public class TestUtil {

  public static ContentAccessor getAccessor(String path) {
    return getAccessor(TestUtil.class, path);
  }

  public static ContentAccessor getAccessor(Class<?> testClass, String fileName) {
    URL url = testClass.getClassLoader().getResource(fileName);
    String str = URLDecoder.decode(url.getFile(), StandardCharsets.UTF_8);
    return new ContentAccessorFile(new File(str));
  }

}
