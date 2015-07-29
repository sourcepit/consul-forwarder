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

import static org.apache.commons.lang.StringUtils.isNotEmpty;
import static org.sourcepit.common.json.GsonBuilder.GSON;
import static org.sourcepit.consul.forwarder.GsonUtils.getAsJsonArray;
import static org.sourcepit.consul.forwarder.GsonUtils.getAsJsonObject;
import static org.sourcepit.consul.forwarder.GsonUtils.getAsString;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.junit.Test;
import org.sourcepit.common.json.GsonBuilder;
import org.sourcepit.docker.watcher.ConsulService;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class ConsulForwarderStateTest {

   @Test
   public void test() {

      ConsulForwarderState state = new ConsulForwarderState();

      JsonObject container = GSON.beginObject()
         .setField("Id", "d8a2ddeca0b090719e9461e11a4c5fa03d1a1f1c5b29cde22852f3bc91475bdb")
         .setField("Name", "/consul-agent")
         .setField("Image", "e66fb678762817bba182eaf192849739f9da1db125b3d1f114000b375b1d1904")
         .setField(
            "Config",
            GSON.beginObject()
               .setField("Image", "progrium/consul")
               .setField("Hostname", "core01")
               .setField(
                  "Env",
                  GSON.beginArray()
                     .add("Foo=bar")
                     .add("PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
                     .add("SHELL=/bin/bash")
                     .endArray())
               .setField(
                  "Labels",
                  GSON.beginObject()
                     .setField("com.example.environment", "production")
                     .setField("com.example.storage", "ssd")
                     .endObject())
               .endObject())
         .setField(
            "HostConfig",
            GSON.beginObject()
               .setField(
                  "PortBindings",
                  GSON.beginObject()
                     .setField("53/udp", newPortBinding("192.168.56.101", "53"))
                     .setField("8500/tcp", newPortBinding("", "8585"))
                     .endObject())
               .endObject())
         .endObject();

      for (ConsulService service : toConsulServices(container)) {
         System.out.println(service);
      }

   }

   private List<ConsulService> toConsulServices(JsonObject container) {
      final List<ConsulService> services = new ArrayList<>();
      final Set<Entry<String, JsonElement>> portBindings = getAsJsonObject(container, "HostConfig", "PortBindings").entrySet();
      if (portBindings.isEmpty()) {
         final ConsulService service = new ConsulService();
         service.name = getServiceName(container);
         service.tags = new ArrayList<>();
         addServiceTags(container, service.tags);
         services.add(service);
      }
      else {
         for (Entry<String, JsonElement> entry : portBindings) {
            final String containerPort = entry.getKey();
            final String[] containerPortToProto = containerPort.split("/");

            final String proto = containerPortToProto[1];

            final JsonObject hostIpAndPort = getAsJsonObject(getAsJsonArray(entry.getValue()).get(0));
            final String hostIp = getAsString(hostIpAndPort, "HostIp");
            final String hostPort = getAsString(hostIpAndPort, "HostPort");

            final ConsulService service = new ConsulService();
            service.name = getServiceName(container, hostPort, proto);
            if (isNotEmpty(hostIp) && !"0.0.0.0".equals(hostIp)) {
               service.address = hostIp;
            }
            service.port = Integer.valueOf(hostPort).intValue();
            service.tags = new ArrayList<>();
            addServiceTags(container, service.tags);
            services.add(service);
         }
      }
      return services;
   }

   private static String getServiceName(JsonObject container, final String port, final String protocol) {
      final StringBuilder sb = new StringBuilder(getServiceName(container));
      sb.append("-");
      sb.append(port);
      if (!"tcp".equals(protocol)) {
         sb.append("-");
         sb.append(protocol);
      }
      return sb.toString();
   }

   private static String getServiceName(JsonObject container) {
      String name = getAsString(container, "Name");
      if (name != null && name.startsWith("/")) {
         name = name.substring(1);
      }
      return name;
   }

   private static void addServiceTags(JsonObject container, Collection<String> tags) {
      final JsonObject config = getAsJsonObject(container, "Config");
      final JsonObject labels = getAsJsonObject(config, "Labels");
      if (labels != null) {
         for (Entry<String, JsonElement> entry : labels.entrySet()) {
            tags.add(entry.getKey() + "=" + getAsString(entry.getValue()));
         }
      }
   }

   private static JsonArray newPortBinding(String hostIp, String hostPort) {
      return GSON.beginArray()
         .addObject()
         .setField("HostIp", hostIp)
         .setField("HostPort", hostPort)
         .endObject()
         .endArray();
   }
}
