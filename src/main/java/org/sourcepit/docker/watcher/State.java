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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

public abstract class State {

   private static final Logger LOG = LoggerFactory.getLogger(State.class);

   private Map<String, JsonObject> state = new HashMap<>();

   public void applyLastKnownState(JsonArray containers) {
      final Map<String, JsonObject> newState = new HashMap<>();
      final Map<String, JsonObject> oldState = new HashMap<>(state);

      final List<JsonObject> events = new ArrayList<>();

      for (JsonElement jsonElement : containers) {
         final JsonObject container = (JsonObject) jsonElement;
         final String id = container.get("Id").getAsString();
         final JsonObject event = event(oldState.remove(id), container);
         if (event != null) {
            events.add(event);
         }
         newState.put(id, container);
      }

      for (JsonObject jsonObject : oldState.values()) {
         final JsonObject event = event(jsonObject, null);
         if (event != null) {
            events.add(event);
         }
      }

      state = newState;
      handle(events);
   }

   protected abstract void handle(final List<JsonObject> events);

   private JsonObject event(JsonObject oldContainer, JsonObject newContainer) {
      if (oldContainer == null) {
         if (newContainer != null) {
            return createEvent(null, null, getStatus(newContainer), newContainer);
         }
      }

      if (newContainer == null) {
         if (oldContainer != null) {
            return createEvent(getStatus(oldContainer), oldContainer, null, null);
         }
      }

      final String oldStatus = getStatus(oldContainer);
      final String newStatus = getStatus(newContainer);
      return createEvent(oldStatus, oldContainer, newStatus, newContainer);
   }

   private static JsonObject createEvent(final String oldStatus, JsonObject oldDetail, final String newStatus,
      JsonObject newDetail) {
      JsonObject event = new JsonObject();
      if (oldStatus == null) {
         event.add("OldState", JsonNull.INSTANCE);
      }
      else {
         event.addProperty("OldState", oldStatus);
      }
      if (newStatus == null) {
         event.add("NewState", JsonNull.INSTANCE);
      }
      else {
         event.addProperty("NewState", newStatus);
      }
      event.add("OldDetail", oldDetail == null ? JsonNull.INSTANCE : oldDetail);
      event.add("NewDetail", newDetail == null ? JsonNull.INSTANCE : newDetail);
      return event;
   }

   private String getStatus(JsonObject container) {
      final String currentStatus = container.get("Status").getAsString();
      if (currentStatus.startsWith("Exited ")) {
         return "STOPPED";
      }
      else if (currentStatus.startsWith("Up ")) {
         return "RUNNING";
      }
      else {
         throw new UnsupportedOperationException("Unknown container status: " + currentStatus);
      }
   }
}
