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

import java.util.concurrent.BlockingQueue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public abstract class SyncStateCommand implements Runnable {

   final BlockingQueue<JsonElement> queue;

   public SyncStateCommand(BlockingQueue<JsonElement> queue) {
      this.queue = queue;
   }

   @Override
   public void run() {

      JsonArray status = null;
      boolean eventDetected = false;

      JsonElement element = queue.poll();
      while (element != null) {
         if (element instanceof JsonObject) {
            eventDetected = true;
         }
         else {
            status = (JsonArray) element;
         }
         element = queue.poll();
      }

      if (status != null) {
         applyLastKnownState(status);
      }

      if (eventDetected) {
         requestNewStatus();
      }
   }

   protected abstract void requestNewStatus();

   protected abstract void applyLastKnownState(JsonArray status);

}
