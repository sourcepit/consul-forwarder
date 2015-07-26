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

import java.io.IOException;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

public class Main {

   private static final Logger LOG = LoggerFactory.getLogger(Main.class);

   public static void main(String[] args) throws IOException {

      final HttpClientFactory clientFactory = new HttpClientFactory() {
         @Override
         public CloseableHttpClient createHttpClient() {
            return HttpClients.createDefault();
         }
      };

      final String dockerDaemonUri = "http://192.168.56.101:2375";
      final String consulAgentUri = "http://192.168.56.101:8500";

      final BlockingQueue<List<JsonObject>> queue = new LinkedBlockingQueue<>();

      final ConsulForwarder consulForwarder = new ConsulForwarder(clientFactory.createHttpClient(), consulAgentUri);

      final Thread containerStateDispatcher = new Thread("Consul Forwarder") {
         @Override
         public void run() {
            while (true) {
               try {
                  consulForwarder.forward(queue.take());
               }
               catch (InterruptedException e) {
                  break;
               }
               catch (Exception e) {
                  LOG.error("Error while forwarding Docker container state to Consul.", e);
               }
            }
         }
      };
      containerStateDispatcher.start();

      final DockerWatcher watcher = new DockerWatcher(clientFactory, dockerDaemonUri) {
         @Override
         protected void handle(List<JsonObject> containerState) {
            queue.add(containerState);
         }
      };

      Runtime.getRuntime().addShutdownHook(new Thread() {
         @Override
         public void run() {
            watcher.stop();
            while (containerStateDispatcher.isAlive()) {
               containerStateDispatcher.interrupt();
               try {
                  Thread.sleep(100L);
               }
               catch (InterruptedException e) {
               }
            }
         }
      });

      watcher.start();
   }
}
