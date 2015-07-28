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

import java.io.IOException;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;

import com.google.gson.JsonElement;

public abstract class AbstractHttpGetCmd<Content extends JsonElement> implements Runnable {

   protected final HttpClient httpClient;

   protected AbstractHttpGetCmd(HttpClient httpClient) {
      this.httpClient = httpClient;
   }

   @Override
   public void run() {
      try {
         handle(httpClient.execute(createRequest(), JsonResponseHandler.<Content> toJson()));
      }
      catch (IOException e) {
         handle(e);
      }
   }

   protected abstract HttpGet createRequest();

   protected abstract void handle(Content body);

   protected abstract void handle(IOException e);

}
