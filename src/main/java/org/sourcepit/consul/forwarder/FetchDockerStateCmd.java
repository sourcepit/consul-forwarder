/*
 * Copyright 2015 Bernd Vogt and others.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sourcepit.consul.forwarder;

import java.io.IOException;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public abstract class FetchDockerStateCmd extends AbstractHttpGetCmd<JsonArray> {

   private static final Logger LOG = LoggerFactory.getLogger(FetchDockerStateCmd.class);

   private final String uri;

   public FetchDockerStateCmd(HttpClient httpClient, String uri) {
      super(httpClient);
      this.uri = uri;
   }

   @Override
   protected HttpGet createRequest() {
      return new HttpGet(uri + "/containers/json?all=1");
   }

   @Override
   protected void handle(JsonArray containers) {
      final JsonArray result = new JsonArray();
      for (JsonElement elem : containers) {
         final JsonObject container = elem.getAsJsonObject();
         final String containerId = container.get("Id").getAsString();
         new InspectContainerCmd(httpClient, uri, containerId) {
            @Override
            protected void handle(JsonObject body) {
               result.add(body);
            }
         }.run();
      }
      doHandle(result);
   }

   protected abstract void doHandle(final JsonArray dockerState);

   @Override
   protected void handle(IOException e) {
      LOG.error("Failed to fetch current Docker state.", e);
   }

}
