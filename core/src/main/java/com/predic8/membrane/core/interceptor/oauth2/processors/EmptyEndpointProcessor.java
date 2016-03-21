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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager;
import com.predic8.membrane.core.interceptor.oauth2.*;
import com.predic8.membrane.core.interceptor.oauth2.flows.CodeFlow;
import com.predic8.membrane.core.interceptor.oauth2.flows.IdTokenTokenFlow;
import com.predic8.membrane.core.interceptor.oauth2.flows.TokenFlow;
import com.predic8.membrane.core.interceptor.oauth2.parameter.ClaimsParameter;
import com.predic8.membrane.core.util.functionalInterfaces.Function;

import java.io.UnsupportedEncodingException;
import java.util.HashSet;

public class EmptyEndpointProcessor extends EndpointProcessor {

    public EmptyEndpointProcessor(OAuth2AuthorizationServerInterceptor authServer) {
        super(authServer);
    }

    @Override
    public boolean isResponsible(Exchange exc) {
        SessionManager.Session s = getSession(exc);
        return exc.getRequestURI().equals("/") && s != null && s.isPreAuthorized();
    }

    @Override
    public Outcome process(Exchange exc) throws Exception {
        SessionManager.Session s = getSession(exc);
        synchronized (s) {
            if (!OAuth2Util.isOpenIdScope(s.getUserAttributes().get(ParamNames.SCOPE)))
                s.getUserAttributes().put("consent", "true");
            if (!s.getUserAttributes().containsKey("consent")) {
                addConsentPageDataToSession(s);
                return redirectToConsentPage(exc);
            }
            if (s.getUserAttributes().get("consent").equals("true")) {
                s.authorize();
                return startOAuth2Flow(exc, s);
            }
        }
        return createParameterizedJsonErrorResponse(exc, "error", "consent_required");
    }

    private Outcome startOAuth2Flow(Exchange exc, SessionManager.Session s) throws Exception {
        if (getResponseType(s).equals("code"))
            return new CodeFlow(authServer, exc, s).getResponse();
        if (getResponseType(s).equals("token"))
            return new TokenFlow(authServer, exc, s).getResponse();
        if(getResponseType(s).equals("id_token token"))
            return new IdTokenTokenFlow(authServer,exc,s).getResponse();
        return createParameterizedJsonErrorResponse(exc, "error", "unsupported_response_type");
    }

    private void addConsentPageDataToSession(SessionManager.Session s) throws UnsupportedEncodingException {
        s.getUserAttributes().put(ConsentPageFile.PRODUCT_NAME, authServer.getConsentPageFile().getProductName());
        s.getUserAttributes().put(ConsentPageFile.LOGO_URL,authServer.getConsentPageFile().getLogoUrl());
        s.getUserAttributes().put(ConsentPageFile.SCOPE_DESCRIPTIONS, getScopeDescriptions(s.getUserAttributes().get(ParamNames.SCOPE).split(" ")));
        s.getUserAttributes().put(ConsentPageFile.CLAIM_DESCRIPTIONS, getClaimDescriptions(processClaimsParameterToClaimsString(s.getUserAttributes().get(ParamNames.CLAIMS))));
    }

    private String[] processClaimsParameterToClaimsString(String claimsParam) {
        ClaimsParameter cp = new ClaimsParameter(authServer.getClaimList().getSupportedClaims(),claimsParam);
        StringBuilder builder = new StringBuilder();

        HashSet<String> userinfo = cp.getUserinfoClaims();
        for(String claim : userinfo)
            builder.append(" ").append(claim);

        HashSet<String> idToken = cp.getIdTokenClaims();
        for(String claim : idToken)
            builder.append(" ").append(claim);

        return builder.toString().trim().split(" ");
    }

    private String getClaimDescriptions(String[] claims) throws UnsupportedEncodingException {
        return createDescription(claims, new Function<String, String>() {
            @Override
            public String call(String param) {
                return ClaimRenamer.convert(param);
            }
        },new Function<String, String>() {
            @Override
            public String call(String claimParam) {
                return authServer.getConsentPageFile().convertClaim(ClaimRenamer.convert(claimParam));
            }
        });
    }

    private String getScopeDescriptions(String[] scopes) throws UnsupportedEncodingException {
        return createDescription(scopes, new Function<String, String>() {
            @Override
            public String call(String param) {
                if(param.equals("openid"))
                    return "";
                return param;
            }
        },new Function<String, String>() {
            @Override
            public String call(String scopeParam) {
                return authServer.getConsentPageFile().convertScope(scopeParam);
            }
        });
    }

    private String createDescription(String[] params, Function<String,String> paramNameConverter, Function<String,String> paramValueConverter) throws UnsupportedEncodingException {
        StringBuilder builder = new StringBuilder();
        HashSet<String> alreadyAddedParams = new HashSet<String>();
        for(String param : params) {
            String correctedParamName = paramNameConverter.call(param);
            if(!correctedParamName.isEmpty() && !alreadyAddedParams.contains(correctedParamName)){
                alreadyAddedParams.add(correctedParamName);
                builder.append(" ").append(correctedParamName).append(" ").append(OAuth2Util.urlencode(paramValueConverter.call(param)));
            }
        }
        return builder.toString().trim();
    }


    private Outcome redirectToConsentPage(Exchange exc) {
        exc.setResponse(Response.redirect("/login/consent",false).dontCache().bodyEmpty().build());
        return Outcome.RETURN;
    }

    protected static String getResponseType(SessionManager.Session s) {
        synchronized(s) {
            return s.getUserAttributes().get("response_type");
        }
    }










}
