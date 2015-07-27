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

import java.util.concurrent.BlockingQueue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public abstract class DispatchItemsCmd implements Runnable {
   private final BlockingQueue<JsonObject> queue;

   public DispatchItemsCmd(BlockingQueue<JsonObject> queue) {
      this.queue = queue;
   }

   @Override
   public void run() {

      JsonArray dockerState = null;
      JsonArray dockerEvents = new JsonArray();
      JsonObject consulState = null;

      JsonObject item = queue.poll();
      while (item != null) {
         final String itemType = item.get("type").getAsString();
         final JsonElement itemData = item.get("data");
         switch (itemType) {
            case "DockerState" :
               dockerState = itemData.getAsJsonArray();
               break;
            case "DockerEvent" :
               dockerEvents.add(itemData);
               break;
            case "ConsulState" :
               consulState = itemData.getAsJsonObject();
               break;
            default :
               throw new IllegalStateException();
         }
         item = queue.poll();
      }

      if (dockerState != null) {
         handleDockerState(dockerState);
      }

      if (dockerEvents.size() > 0) {
         handleDockerEvents(dockerEvents);
      }

      if (consulState != null) {
         handleConsulState(consulState);
      }
   }

   protected abstract void handleDockerState(JsonArray dockerState);

   protected abstract void handleDockerEvents(JsonArray dockerEvents);

   protected abstract void handleConsulState(JsonObject consulState);
}