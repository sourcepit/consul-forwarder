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

import static org.apache.commons.lang.Validate.isTrue;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public abstract class DockerWatcher {
   private static final Logger LOG = LoggerFactory.getLogger(DockerWatcher.class);

   private final HttpClientFactory clientFactory;

   private final String uri;

   private Thread eventObserverThread;

   private ScheduledExecutorService scheduler;

   private CloseableHttpClient client;

   public DockerWatcher(HttpClientFactory clientFactory, String uri) {
      this.clientFactory = clientFactory;
      this.uri = uri;
   }

   public synchronized void start() {
      isTrue(client == null);
      isTrue(eventObserverThread == null);
      isTrue(scheduler == null);

      final State state = new State() {
         @Override
         protected void handle(List<JsonObject> events) {
            DockerWatcher.this.handle(events);
         }
      };

      final BlockingQueue<JsonElement> queue = new LinkedBlockingQueue<>();

      client = clientFactory.createHttpClient();

      final FetchConatinersCommand fetchContainersCommand = new FetchConatinersCommand(client, uri) {
         @Override
         protected void handle(JsonArray status) {
            LOG.debug("Fetched: {}", status.toString());
            queue.add(status);
         }
      };

      final DockerEventObserver eventObserver = new DockerEventObserver(client, uri) {
         @Override
         protected void handle(JsonObject event) {
            queue.add(event);
         }
      };

      eventObserverThread = new Thread(eventObserver, "Docker Event Observer") {
         @Override
         public void interrupt() {
            eventObserver.die();
            super.interrupt();
         }
      };

      scheduler = Executors.newScheduledThreadPool(1, new ThreadFactory() {
         @Override
         public Thread newThread(Runnable r) {
            return new Thread(r, "Docker State Fetcher");
         }
      });

      final SyncStateCommand syncStateCommand = new SyncStateCommand(queue) {

         @Override
         protected void requestNewStatus() {
            LOG.debug("Requesting new status.");
            scheduler.execute(fetchContainersCommand);
         }

         @Override
         protected void applyLastKnownState(JsonArray status) {
            LOG.debug("Applying new status: {}", status.toString());
            state.applyLastKnownState(status);
         }

      };

      scheduler.scheduleWithFixedDelay(fetchContainersCommand, 0, 30, TimeUnit.SECONDS);
      scheduler.scheduleWithFixedDelay(syncStateCommand, 0, 1, TimeUnit.SECONDS);
      eventObserverThread.start();
   }

   protected abstract void handle(List<JsonObject> stateEvents);

   public synchronized void stop() {
      if (scheduler != null) {
         try {
            scheduler.shutdownNow();
         }
         catch (Exception e) {
         }
         scheduler = null;
      }

      if (client != null) {
         IOUtils.closeQuietly(client);
         client = null;
      }

      if (eventObserverThread != null) {
         try {
            while (eventObserverThread.isAlive()) {
               eventObserverThread.interrupt();
               try {
                  Thread.sleep(100L);
               }
               catch (InterruptedException e) {
                  break;
               }
            }
         }
         catch (Exception e) {
         }
      }
   }
}
