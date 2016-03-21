/*
 * Copyright 2016 predic8 GmbH, www.predic8.com
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.interceptor.oauth2.processors;

import com.fasterxml.jackson.core.JsonGenerator;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AuthorizationServerInterceptor;
import com.predic8.membrane.core.interceptor.oauth2.ReusableJsonGenerator;
import com.predic8.membrane.core.util.URIFactory;

import java.io.IOException;

public abstract class EndpointProcessor {

    protected final OAuth2AuthorizationServerInterceptor authServer;
    URIFactory uriFactory;
    ReusableJsonGenerator jsonGen = new ReusableJsonGenerator();

    public abstract boolean isResponsible(Exchange exc);
    public abstract Outcome process(Exchange exc) throws Exception;

    public EndpointProcessor(OAuth2AuthorizationServerInterceptor authServer){
        this.authServer = authServer;
        uriFactory = authServer.getRouter().getUriFactory();
    }

    public Outcome createParameterizedJsonErrorResponse(Exchange exc, String... params) throws IOException {
        if (params.length % 2 != 0)
            throw new IllegalArgumentException("The number of strings passed as params is not even");
        String json;
        synchronized (jsonGen) {
            JsonGenerator gen = jsonGen.resetAndGet();
            gen.writeStartObject();
            for (int i = 0; i < params.length; i += 2)
                gen.writeObjectField(params[i], params[i + 1]);
            gen.writeEndObject();
            json = jsonGen.getJson();
        }

        exc.setResponse(Response.badRequest()
                .body(json)
                .contentType(MimeType.APPLICATION_JSON_UTF8)
                .dontCache()
                .build());

        return Outcome.RETURN;
    }

    protected SessionManager.Session getSession(Exchange exc) {
        SessionManager.Session session;
        synchronized (authServer.getSessionManager()) {
            session = authServer.getSessionManager().getSession(exc.getRequest());
        }
        return session;
    }

}
