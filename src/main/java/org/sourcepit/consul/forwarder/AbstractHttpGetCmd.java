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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

public abstract class AbstractHttpGetCmd<Body extends JsonElement> implements Runnable {

   protected final HttpClient httpClient;

   protected AbstractHttpGetCmd(HttpClient httpClient) {
      this.httpClient = httpClient;
   }

   @SuppressWarnings("unchecked")
   @Override
   public void run() {
      try {
         final HttpResponse response = httpClient.execute(createRequest());
         try (JsonReader jsonReader = createJsonReader(response)) {
            handle((Body) new JsonParser().parse(jsonReader));
         }
      }
      catch (IOException e) {
         handle(e);
      }
   }

   protected abstract HttpGet createRequest();

   protected abstract void handle(Body body);

   protected abstract void handle(IOException e);

   private static JsonReader createJsonReader(HttpResponse response) throws IOException {
      return new JsonReader(new BufferedReader(new InputStreamReader(response.getEntity().getContent(), "UTF-8")));
   }

}
