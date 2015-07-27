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
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sourcepit.docker.watcher.DockerEventObserver;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class Main {
   private static final Logger LOG = LoggerFactory.getLogger(Main.class);

   public static void main(String[] args) {

      final String dockerUri = "http://192.168.56.101:2375";
      final String consulUri = "http://192.168.56.101:8500";

      final int fetchConsulStateInterval = 30;
      final int fetchDockerStateInterval = 30;
      final int dispatchItemsInterval = 2;
      final int requestDockerStateDelay = 5;

      PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
      connManager.setDefaultMaxPerRoute(10);
      final HttpClient httpClient = HttpClients.createMinimal(connManager);

      final BlockingQueue<JsonObject> queue = new LinkedBlockingQueue<>();

      final FetchConsulStateCmd fetchConsulStateCmd = new FetchConsulStateCmd(httpClient, consulUri) {
         @Override
         protected void doHandle(JsonObject consulState) {
            final JsonObject item = createItem("ConsulState", consulState);
            LOG.debug(item.toString());
            queue.add(item);
         }

      };

      final FetchDockerStateCmd fetchDockerStateCmd = new FetchDockerStateCmd(httpClient, dockerUri) {
         @Override
         protected void doHandle(JsonArray dockerState) {
            final JsonObject item = createItem("DockerState", dockerState);
            LOG.debug(item.toString());
            queue.add(item);
         }
      };

      final ConsulForwarderState forwarderState = new ConsulForwarderState();

      final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
      scheduler.scheduleAtFixedRate(fetchConsulStateCmd, 0, fetchConsulStateInterval, TimeUnit.SECONDS);
      scheduler.scheduleAtFixedRate(fetchDockerStateCmd, 0, fetchDockerStateInterval, TimeUnit.SECONDS);
      scheduler.scheduleAtFixedRate(new DispatchItemsCmd(queue) {
         @Override
         protected void handleDockerState(JsonArray dockerState) {
            forwarderState.applyDockerState(dockerState);
         }

         @Override
         protected void handleDockerEvents(JsonArray dockerEvents) {
            // trigger docker state update
            scheduler.schedule(fetchDockerStateCmd, requestDockerStateDelay, TimeUnit.SECONDS);
         }

         @Override
         protected void handleConsulState(JsonObject consulState) {
            forwarderState.applyConsulState(consulState);
         }
      }, 0, dispatchItemsInterval, TimeUnit.SECONDS);

      final DockerEventObserver eventObserver = new DockerEventObserver(httpClient, dockerUri) {
         @Override
         protected void handle(JsonObject event) {
            queue.add(createItem("DockerEvent", event));
         }
      };

      final Thread eventObserverThread = new Thread(eventObserver, "Docker Event Observer") {
         @Override
         public void interrupt() {
            eventObserver.die();
            super.interrupt();
         }
      };
      eventObserverThread.start();
   }

   private static JsonObject createItem(String type, JsonElement data) {
      final JsonObject item = new JsonObject();
      item.addProperty("type", type);
      item.add("data", data);
      return item;
   }
}
