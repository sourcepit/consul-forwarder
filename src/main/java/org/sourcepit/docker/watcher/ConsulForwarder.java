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

import static org.apache.commons.lang.Validate.notNull;
import static org.apache.http.client.utils.HttpClientUtils.closeQuietly;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class ConsulForwarder {

   private static final Logger LOG = LoggerFactory.getLogger(ConsulForwarder.class);

   private final HttpClient client;

   private final String uri;

   public ConsulForwarder(HttpClient client, String uri) {
      this.client = client;
      this.uri = uri;
   }

   public void forward(List<JsonObject> containerState) {
      for (JsonObject event : containerState) {

         final String oldState = getAsString(event, "OldState");
         final String newState = getAsString(event, "NewState");

         JsonObject container = getAsJsonObject(event, "NewDetail");
         if (container == null) {
            container = getAsJsonObject(event, "OldDetail");
         }
         notNull(container);

         final List<ConsulService> services = toServices(container);


         for (ConsulService service : services) {
            if (oldState == null) {
               notNull(newState);
               LOG.info("REGISTER {}", service);
               register(service);
            }
            else if (newState == null) {
               notNull(oldState);
               LOG.info("DEREGISTER {}", service);
               deregister(service);
            }

            if ("RUNNING".equals(newState)) {
               LOG.info("TTL PASS {}", service);
               pass(service);
            }
            else if ("STOPPED".equals(newState)) {
               LOG.info("TTL FAIL {}", service);
               fail(service);
            }
         }
      }
   }

   private void fail(ConsulService service) {
      HttpGet get = new HttpGet(uri + "/v1/agent/check/fail/" + getTtlCheckName(service));
      try {
         closeQuietly(client.execute(get));
      }
      catch (IOException e) {
         e.printStackTrace();
      }
   }

   private void pass(ConsulService service) {
      HttpGet get = new HttpGet(uri + "/v1/agent/check/pass/" + getTtlCheckName(service));
      try {
         closeQuietly(client.execute(get));
      }
      catch (IOException e) {
         e.printStackTrace();
      }
   }

   private void deregister(ConsulService service) {
      final HttpDelete delete = new HttpDelete(uri + "/v1/agent/service/deregister/" + id(service));
      try {
         closeQuietly(client.execute(delete));
      }
      catch (IOException e) {
         e.printStackTrace();
      }

      final HttpDelete delete2 = new HttpDelete(uri + "/v1/agent/service/deregister/" + getTtlCheckName(service));
      try {
         closeQuietly(client.execute(delete2));
      }
      catch (IOException e) {
         e.printStackTrace();
      }
   }

   private String getTtlCheckName(ConsulService service) {
      return "ttl-" + id(service);
   }

   private void register(ConsulService service) {

      final HttpPut put = new HttpPut(uri + "/v1/agent/service/register");
      put.setEntity(new StringEntity(service.toString(), ContentType.APPLICATION_JSON));
      try {
         HttpResponse response = client.execute(put);
         closeQuietly(response);
      }
      catch (IOException e) {
         e.printStackTrace();
      }

      final TtlCheck check = new TtlCheck();
      check.serviceId = id(service);
      check.name = getTtlCheckName(service);
      check.id = getTtlCheckName(service);
      check.ttl = "90s";

      final HttpPut put2 = new HttpPut(uri + "/v1/agent/check/register");
      put2.setEntity(new StringEntity(check.toString(), ContentType.APPLICATION_JSON));
      try {
         HttpResponse response = client.execute(put2);
         closeQuietly(response);
      }
      catch (IOException e) {
         e.printStackTrace();
      }
   }

   public String id(ConsulService service) {
      return service.id == null ? service.name : service.id;
   }

   private List<ConsulService> toServices(JsonObject container) {
      final List<ConsulService> services = new ArrayList<>();

      final String name = getServiceName(container);
      final JsonArray portDefs = getAsJsonArray(container, "Ports");
      if (portDefs.size() > 0) {
         for (JsonElement elem : portDefs) {
            final JsonObject portDef = getAsJsonObject(elem);
            if (portDef != null) {
               final Integer port = getAsInteger(portDef, "PublicPort");
               if (port != null) {
                  String type = getAsString(portDef, "Type");
                  final ConsulService service = new ConsulService();
                  service.port = port.intValue();
                  service.name = name + "-" + service.port;
                  service.address = getAsString(portDef, "IP");
                  if (StringUtils.isNotEmpty(type)) {
                     service.tags = new ArrayList<>(1);
                     service.tags.add("PortType=" + type);

                     service.name += "-" + type;
                  }
                  services.add(service);
               }
            }
         }
      }
      else {
         final ConsulService service = new ConsulService();
         service.name = name;
         services.add(service);
      }
      return services;
   }

   private String getServiceName(JsonObject container) {
      JsonArray names = getAsJsonArray(container, "Names");
      String name = names.size() > 0 ? getAsString(names.get(0)) : null;
      if (name != null && name.startsWith("/")) {
         name = name.substring(1);
      }
      return name;
   }

   private JsonArray getAsJsonArray(JsonObject object, String member) {
      final JsonElement jsonElement = object.get(member);
      if (jsonElement == null || jsonElement.isJsonNull()) {
         return new JsonArray();
      }
      return jsonElement.getAsJsonArray();
   }

   private JsonObject getAsJsonObject(JsonObject object, String member) {
      return getAsJsonObject(object.get(member));
   }

   private JsonObject getAsJsonObject(final JsonElement jsonElement) {
      if (jsonElement == null || jsonElement.isJsonNull()) {
         return null;
      }
      return jsonElement.getAsJsonObject();
   }

   private String getAsString(JsonObject object, String member) {
      return getAsString(object.get(member));
   }

   private String getAsString(JsonElement jsonElement) {
      if (jsonElement == null || jsonElement.isJsonNull()) {
         return null;
      }
      return jsonElement.getAsString();
   }

   private Integer getAsInteger(JsonObject object, String member) {
      return getAsInteger(object.get(member));
   }

   private Integer getAsInteger(JsonElement jsonElement) {
      if (jsonElement == null || jsonElement.isJsonNull()) {
         return null;
      }
      return Integer.valueOf(jsonElement.getAsInt());
   }

}
