package org.pentaho.di.sdk.samples.steps.ntmlclient.utils;

import org.apache.http.HttpEntity;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.sdk.samples.steps.ntmlclient.NtmlclientStepMeta;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class NtlmUtils {
    private NtlmUtils() {
        throw new AssertionError();
    }

    private static final Class<?> PKG = NtmlclientStepMeta.class;

    private static final int MAX_TIME_OUT = 60000;

    public static CloseableHttpClient setHttpClient() {
        RequestConfig requestConfig = RequestConfig.custom()
                .setSocketTimeout( MAX_TIME_OUT )
                .setConnectTimeout( MAX_TIME_OUT )
                .build();

        return HttpClients.custom()
                .setDefaultRequestConfig( requestConfig )
                .build();
    }

    public static RequestBuilder setRequestBuilder(String method) throws KettleException {
        switch ( method ) {
            case NtmlclientStepMeta.HTTP_METHOD_GET :
                return RequestBuilder.get();
            case NtmlclientStepMeta.HTTP_METHOD_POST:
                return RequestBuilder.post();
            case NtmlclientStepMeta.HTTP_METHOD_PUT:
                return RequestBuilder.put();
            case NtmlclientStepMeta.HTTP_METHOD_DELETE:
                return RequestBuilder.delete();
            case NtmlclientStepMeta.HTTP_METHOD_HEAD:
                return RequestBuilder.head();
            case NtmlclientStepMeta.HTTP_METHOD_OPTIONS:
                return RequestBuilder.options();
            case NtmlclientStepMeta.HTTP_METHOD_PATCH:
                return RequestBuilder.patch();
            default:
                throw new KettleException( BaseMessages.getString( PKG, "Rest.Error.UnknownMethod", method ) );
        }
    }

    public static HttpUriRequest setRequest( RequestBuilder builder, String url ) {
        return builder.setUri( url ).build();
    }

    public static CloseableHttpResponse getResponse( CloseableHttpClient client, HttpUriRequest request, HttpClientContext context ) throws IOException {
        return client.execute( request, context );
    }

    public static CredentialsProvider getNtmlCredentials( String userName, String password, String domain ) {
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(
                AuthScope.ANY,
                new NTCredentials( userName, password, null, domain )
        );
        return credentialsProvider;
    }

    public static String getHeaderString( CloseableHttpResponse response ) {
        Map<String, String> result = new HashMap<>();
        Arrays.stream(response.getAllHeaders())
                .forEach(it -> result.put( it.getName(), it.getValue() ));
        return result.toString();
    }

    public static String getBodyString ( CloseableHttpResponse response ) throws IOException {
        HttpEntity httpEntity = response.getEntity();
        return EntityUtils.toString( httpEntity, StandardCharsets.UTF_8 );
    }
}
