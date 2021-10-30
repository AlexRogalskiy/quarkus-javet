/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package se.yolean.javet.quarkus.it;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.V8Host;
import com.caoccao.javet.interop.V8Runtime;

@Path("/quarkus-javet")
@ApplicationScoped
public class QuarkusJavetResource {

  V8Host nodeInstance() {
    return V8Host.getNodeInstance();
  }

  V8Host v8Instance() {
    return V8Host.getV8Instance();
  }

  @GET
  @Path("/v8")
  public String v8() throws JavetException {
    try (V8Runtime v8Runtime = v8Instance().createV8Runtime()) {
      return v8Runtime.getExecutor("'Javet V8 mode invoked'").executeString();
    }
  }

  @GET
  @Path("/node")
  public String node() throws JavetException {
    try (V8Runtime v8Runtime = nodeInstance().createV8Runtime()) {
      return v8Runtime.getExecutor("'Javet Node mode invoked'").executeString();
    }
  }

}
