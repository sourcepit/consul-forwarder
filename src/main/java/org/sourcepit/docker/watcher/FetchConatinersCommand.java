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

package org.sourcepit.docker.watcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

public abstract class FetchConatinersCommand implements Runnable {

   private static final Logger LOG = LoggerFactory.getLogger(FetchConatinersCommand.class);

   private final HttpClient client;

   private final String dockerHostUri;

   public FetchConatinersCommand(HttpClient client, String dockerHostUri) {
      this.client = client;
      this.dockerHostUri = dockerHostUri;
   }

   public void run() {
      try {
         final HttpResponse response = client.execute(new HttpGet(dockerHostUri + "/containers/json?all=1"));
         LOG.debug(response.toString());
         try (JsonReader jsonReader = createJsonReader(response)) {
            handle((JsonArray) new JsonParser().parse(jsonReader));
         }
      }
      catch (Exception e) {
         LOG.error("Error while fetching status of docker containers.", e);
      }
   }

   protected abstract void handle(JsonArray status);

   private static JsonReader createJsonReader(HttpResponse response) throws IOException {
      return new JsonReader(new BufferedReader(new InputStreamReader(response.getEntity().getContent(), "UTF-8")));
   }

}
