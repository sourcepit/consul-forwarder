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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class GsonUtils {
   private GsonUtils() {
      super();
   }

   public static JsonArray getAsJsonArray(JsonObject object, String member) {
      return getAsJsonArray(object.get(member));
   }

   public static JsonArray getAsJsonArray(final JsonElement jsonElement) {
      if (jsonElement == null || jsonElement.isJsonNull()) {
         return new JsonArray();
      }
      return jsonElement.getAsJsonArray();
   }

   public static JsonObject getAsJsonObject(JsonObject root, String... path) {
      JsonObject current = root;
      for (String field : path) {
         current = getAsJsonObject(current.get(field));
         if (current == null) {
            return current;
         }
      }
      return current;
   }

   public static JsonObject getAsJsonObject(final JsonElement jsonElement) {
      if (jsonElement == null || jsonElement.isJsonNull()) {
         return null;
      }
      return jsonElement.getAsJsonObject();
   }

   public static String getAsString(JsonObject object, String member) {
      return getAsString(object.get(member));
   }

   public static String getAsString(JsonElement jsonElement) {
      if (jsonElement == null || jsonElement.isJsonNull()) {
         return null;
      }
      return jsonElement.getAsString();
   }

   public static Integer getAsInteger(JsonObject object, String member) {
      return getAsInteger(object.get(member));
   }

   public static Integer getAsInteger(JsonElement jsonElement) {
      if (jsonElement == null || jsonElement.isJsonNull()) {
         return null;
      }
      return Integer.valueOf(jsonElement.getAsInt());
   }
}
