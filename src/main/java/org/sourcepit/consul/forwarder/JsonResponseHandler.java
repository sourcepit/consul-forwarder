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

import org.apache.http.HttpEntity;
import org.apache.http.impl.client.AbstractResponseHandler;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

public class JsonResponseHandler<Content extends JsonElement> extends AbstractResponseHandler<Content> {

   public static <Content extends JsonElement> JsonResponseHandler<Content> toJson() {
      return new JsonResponseHandler<>();
   }

   @SuppressWarnings("unchecked")
   @Override
   public Content handleEntity(HttpEntity entity) throws IOException {
      try (JsonReader jsonReader = createJsonReader(entity)) {
         return (Content) new JsonParser().parse(jsonReader);
      }
   }

   private static JsonReader createJsonReader(HttpEntity entity) throws IOException {
      return new JsonReader(new BufferedReader(new InputStreamReader(entity.getContent(), "UTF-8")));
   }

}
