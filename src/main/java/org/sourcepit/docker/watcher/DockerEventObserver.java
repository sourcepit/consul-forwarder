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
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

public abstract class DockerEventObserver implements Runnable {

   private static final Logger LOG = LoggerFactory.getLogger(DockerEventObserver.class);

   private final HttpClient client;

   private final String dockerHostUri;

   private AtomicBoolean die = new AtomicBoolean(false);

   public DockerEventObserver(HttpClient client, String dockerHostUri) {
      this.client = client;
      this.dockerHostUri = dockerHostUri;
   }

   public void run() {
      while (!die.get()) {
         try {
            watch(client, dockerHostUri);
         }
         catch (Exception e) {
            if (!die.get()) {
               LOG.error("Error while observing docker events. Retrying in 3s.", e);
            }
         }

         if (!die.get()) {
            try {
               Thread.sleep(3000L);
            }
            catch (InterruptedException i) {
               if (!die.get()) {
                  LOG.warn("Observation of docker events was interrupted.", i);
               }
               break;
            }
         }
      }
   }

   public void die() {
      die.set(true);
   }

   private void watch(HttpClient client, String uri) throws IOException, ClientProtocolException {
      HttpResponse response = client.execute(new HttpGet(uri + "/events"));
      LOG.debug(response.toString());
      try (JsonReader jsonReader = createJsonReader(response)) {
         JsonParser jsonParser = new JsonParser();
         JsonObject event = (JsonObject) jsonParser.parse(jsonReader);
         while (event != null) {
            handle(event);
            event = (JsonObject) jsonParser.parse(jsonReader);
         }
      }
   }

   protected abstract void handle(JsonObject event);

   private static JsonReader createJsonReader(HttpResponse response) throws IOException {
      return new JsonReader(new BufferedReader(new InputStreamReader(response.getEntity().getContent(), "UTF-8")));
   }

}
