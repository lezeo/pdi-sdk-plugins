package org.pentaho.di.sdk.samples.steps.ntmlclient;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.api.uri.UriComponent;
import com.sun.jersey.client.apache4.config.ApacheHttpClient4Config;
import com.sun.jersey.client.apache4.config.DefaultApacheHttpClient4Config;
import com.sun.jersey.client.urlconnection.HTTPSProperties;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import org.apache.commons.lang.StringUtils;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.pentaho.di.core.Const;
import org.pentaho.di.core.encryption.Encr;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.exception.KettleStepException;
import org.pentaho.di.core.exception.KettleValueException;
import org.pentaho.di.core.row.RowDataUtil;
import org.pentaho.di.core.row.RowMeta;
import org.pentaho.di.core.row.ValueMetaInterface;
import org.pentaho.di.core.row.value.ValueMetaString;
import org.pentaho.di.core.util.Utils;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.sdk.samples.steps.ntmlclient.utils.JsonUtils;
import org.pentaho.di.sdk.samples.steps.ntmlclient.utils.NtlmUtils;
import org.pentaho.di.sdk.samples.steps.ntmlclient.utils.XmlUtils;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.*;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManagerFactory;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.xml.parsers.ParserConfigurationException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.*;

public class NtmlclientStep extends BaseStep implements StepInterface {
    private static Class<?> PKG = NtmlclientStepMeta.class; // for i18n purposes, needed by Translator2!! $NON-NLS-1$

    private NtmlclientStepMeta meta;
    private NtmlclientStepData data;

    public NtmlclientStep(
            StepMeta stepMeta,
            StepDataInterface stepDataInterface,
            int copyNr,
            TransMeta transMeta,
            Trans trans
    ) {
        super( stepMeta, stepDataInterface, copyNr, transMeta, trans );
    }

    /* for unit test*/
    MultivaluedMapImpl createMultivalueMap(String paramName, String paramValue ) {
        MultivaluedMapImpl queryParams = new MultivaluedMapImpl();
        queryParams.add( paramName, UriComponent.encode( paramValue, UriComponent.Type.QUERY_PARAM ) );
        return queryParams;
    }

    protected List<Object[]> callRest( Object[] rowData ) throws KettleException {
        // get dynamic url ?
        if ( meta.isUrlInField() ) {
            data.realUrl = data.inputRowMeta.getString( rowData, data.indexOfUrlField );
        }

        // get dynamic method?
        if ( meta.isDynamicMethod() ) {
            data.method = data.inputRowMeta.getString( rowData, data.indexOfMethod );
            if ( Utils.isEmpty( data.method ) ) {
                throw new KettleException( BaseMessages.getString( PKG, "Rest.Error.MethodMissing" ) );
            }
        }

        Object[] newRow = Optional.ofNullable( rowData )
                .map(Object[]::clone)
                .orElse( null );

        try ( CloseableHttpClient client = NtlmUtils.setHttpClient() ) {
            if ( isDetailed() ) {
                logDetailed( BaseMessages.getString( PKG, "Rest.Log.ConnectingToURL", data.realUrl ) );
            }
            // Register a custom StringMessageBodyWriter to solve PDI-17423
            MessageBodyWriter<String> stringMessageBodyWriter = new StringMessageBodyWriter();
            data.config.getSingletons().add( stringMessageBodyWriter );

            // create request builder
            RequestBuilder requestBuilder = NtlmUtils.setRequestBuilder( data.method );
            addQueryParam( requestBuilder, rowData );

            HttpClientContext context = HttpClientContext.create();
            addAuthentication( context );

            if ( isDebug() ) {
                logDebug( BaseMessages.getString( PKG, "Rest.Log.ConnectingToURL", data.realUrl ) );
            }
            HttpUriRequest request = NtlmUtils.setRequest( requestBuilder, data.realUrl );
            addHeaders( request );

            // Get Response
            long startTime = System.currentTimeMillis();
            CloseableHttpResponse response = NtlmUtils.getResponse( client, request, context );

            // for output
            return getHttpResult( newRow, response, startTime );
        } catch ( Exception e ) {
            throw new KettleException( BaseMessages.getString( PKG, "Rest.Error.CanNotReadURL", data.realUrl ), e );
        }
    }

    private void addQueryParam( RequestBuilder requestBuilder, Object[] rowData ) throws KettleValueException {
        if ( data.useParams ) {
            for ( int i = 0; i < data.nrParams; i++ ) {
                String value = data.inputRowMeta.getString( rowData, data.indexOfParamFields[i] );
                if ( isDebug() ) {
                    logDebug( BaseMessages.getString( PKG, "Rest.Log.queryParameterValue", data.paramNames[i], value ) );
                }
                requestBuilder.addParameter( data.paramNames[i], value );
            }
        }
    }

    private void addAuthentication( HttpClientContext context ) {
        if ( data.basicAuthentication != null ) {
            String userName = data.realHttpLogin;
            String domain = "";
            if ( userName.contains("/") ) {
                domain = userName.split("/")[0];
                userName = userName.split("/")[1];
            }
            assert userName != null;

            context.setCredentialsProvider( NtlmUtils.getNtmlCredentials( userName, data.realHttpPassword, domain ));
        }
    }

    private void addHeaders( HttpUriRequest request ) {
        if ( data.useHeaders ) {
            for ( int i = 0; i < data.nrheader; i++ ) {
                String result = data.headerNames[i];
                if ( isDebug() ) {
                    logDebug( BaseMessages.getString( PKG, "Rest.Log.HeaderValue", data.headerNames[i], data.headerNames[i] ) );
                }
                String name = result.split(":")[0];
                String value = result.split(":")[1];
                request.addHeader(name, value);
            }
        }
    }

    private List<Object[]> getHttpResult( Object[] newRow, CloseableHttpResponse response, long startTime ) throws IOException, ParserConfigurationException, SAXException {
        String body = NtlmUtils.getBodyString( response );
        String header = NtlmUtils.getHeaderString( response );

        // Get response time
        long responseTime = System.currentTimeMillis() - startTime;
        if ( isDetailed() ) {
            logDetailed( BaseMessages.getString( PKG, "Rest.Log.ResponseTime", String.valueOf( responseTime ), data.realUrl ) );
        }

        // Get status, Display status code
        int status = response.getStatusLine().getStatusCode();
        if ( isDebug() ) {
            logDebug( BaseMessages.getString( PKG, "Rest.Log.ResponseCode", "" + status ) );
        }

        if ( !Utils.isEmpty( data.resultFieldName ) ) {
            int index = data.outputRowMeta.indexOfValue( data.resultFieldName );
            newRow = RowDataUtil.addValueData( newRow, index, body );
        }

        // add status to output
        if ( !Utils.isEmpty( data.resultCodeFieldName ) ) {
            int index = data.outputRowMeta.indexOfValue( data.resultCodeFieldName );
            newRow = RowDataUtil.addValueData( newRow, index, (long) status);
        }

        // add response time to output
        if ( !Utils.isEmpty( data.resultResponseFieldName ) ) {
            int index = data.outputRowMeta.indexOfValue( data.resultResponseFieldName );
            newRow = RowDataUtil.addValueData( newRow, index, responseTime);
        }

        // add response header to output
        if ( !Utils.isEmpty( data.resultHeaderFieldName ) ) {
            int index = data.outputRowMeta.indexOfValue( data.resultHeaderFieldName );
            newRow = RowDataUtil.addValueData( newRow, index, header );
        }

        if ( StringUtils.isNotBlank( body ) ) {
            if ( header.contains( "xml" ) ) {
                return parseXml( body, newRow );
            }
            if ( header.contains( "json") ) {
                return parseJson( body, newRow );
            }
        }
        return Collections.emptyList();
    }

    private List<Object[]> parseXml( String body, Object[] newRow ) throws ParserConfigurationException, IOException, SAXException {
        List<Object[]> result = new ArrayList<>();
        byte[] bytes = body.getBytes( StandardCharsets.UTF_8 );
        Document document = XmlUtils.xmlParse( bytes );
        NodeList nodeList = document.getElementsByTagName( "entry" );

        for (int i = 0; i < nodeList.getLength(); i++ ) {
            Node tmpNode = nodeList.item(i).getAttributes().getNamedItem("m:etag");
            if (tmpNode!=null) {
                NodeList childes = nodeList.item(i).getChildNodes();
                Object[] row = newRow.clone();
                Map<String, Object> resultMap = new HashMap<>(0);
                for (int f = 0; f < childes.getLength(); f++) {
                    Node node = childes.item(f);
                    if (node.getNodeName().equalsIgnoreCase("#text")) {
                        continue;
                    }
                    XmlUtils.recursiveNodeVal(node, false, null, resultMap);
                }

                if (!CollectionUtils.isEmpty(resultMap)) {
                    for (Map.Entry<String,Object> entry : resultMap.entrySet()) {
                        ValueMetaInterface v = new ValueMetaString(entry.getKey());
                        v.setOrigin(getStepname());

                        if (data.outputRowMeta.exists(v)) {
                            int index = data.outputRowMeta.indexOfValue(entry.getKey());
                            row = RowDataUtil.addValueData(row, index, entry.getValue());
                        }
                    }
                    result.add( row );
                }
            }
        }
        return result;
    }

    private List<Object[]> parseJson( String body, Object[] newRow ) {
        List<Object[]> rows = new ArrayList<>();
        JSONObject result = JSON.parseObject( body );
        Assert.notNull( result, "解析异常" );

        Map.Entry< String, Object > dataEntry = result.entrySet()
                .stream()
                .filter( it -> it.getKey().equals( "data" ) )
                .findFirst()
                .orElse(null);
        Assert.notNull( dataEntry, "result set is null" );

        boolean isArray = JsonUtils.isJSONArray( dataEntry.getValue() );
        if ( isArray ) {
            JSONArray array = JSON.parseArray( dataEntry.getValue().toString() );
            parseJsonArray( array, newRow, rows );
        } else {
            JSONObject object = JSON.parseObject( dataEntry.getValue().toString() );
            parseJsonObject( object, newRow, rows );
        }
        return rows;
    }

    private void parseJsonArray( JSONArray array, Object[] newRow, List<Object[]> rows ) throws JSONException {
        for ( Object o : array ) {
            JSONObject object = JSON.parseObject( o.toString() );
            boolean isExist = false;
            Object[] row = newRow.clone();

            for ( Map.Entry<String, Object> item : object.entrySet() ) {
                ValueMetaInterface v = new ValueMetaString( item.getKey() );
                v.setOrigin( getStepname() );
                if ( data.outputRowMeta.exists( v ) ) {
                    isExist = true;
                    int index = data.outputRowMeta.indexOfValue( item.getKey() );
                    row = RowDataUtil.addValueData( row, index, item.getValue() );
                }
            }

            if ( isExist ) {
                rows.add( row );
                if ( checkFeedback( getLinesRead() ) && isDetailed() ) {
                    logDetailed( BaseMessages.getString( PKG, "Rest.LineNumber" ) + getLinesRead() );
                }
            }
        }
    }

    private void parseJsonObject( JSONObject object, Object[] newRow, List<Object[]> rows ) {
        for ( Map.Entry<String, Object> item : object.entrySet() ) {
            ValueMetaInterface v = new ValueMetaString( item.getKey() );
            v.setOrigin( getStepname() );

            if ( data.outputRowMeta.exists( v ) ) {
                int index = data.outputRowMeta.indexOfValue( item.getKey() );
                newRow = RowDataUtil.addValueData( newRow, index, item.getValue() );
                rows.add( newRow );
            }
        }
    }

    private void setConfig() throws KettleException {
        if ( data.config == null ) {
            // Use ApacheHttpClient for supporting proxy authentication.
            data.config = new DefaultApacheHttpClient4Config();
            if ( !Utils.isEmpty( data.realProxyHost ) ) {
                // PROXY CONFIGURATION
                data.config.getProperties().put( ApacheHttpClient4Config.PROPERTY_PROXY_URI, "http://" + data.realProxyHost + ":" + data.realProxyPort );
                if ( !Utils.isEmpty( data.realHttpLogin ) && !Utils.isEmpty( data.realHttpPassword ) ) {
                    AuthScope authScope = new AuthScope( data.realProxyHost, data.realProxyPort );
                    UsernamePasswordCredentials credentials = new UsernamePasswordCredentials( data.realHttpLogin, data.realHttpPassword );
                    CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                    credentialsProvider.setCredentials( authScope, credentials );
                    data.config.getProperties().put( ApacheHttpClient4Config.PROPERTY_CREDENTIALS_PROVIDER, credentialsProvider );
                }
            } else {
                if ( !Utils.isEmpty( data.realHttpLogin ) ) {
                    // Basic authentication
                    data.basicAuthentication = new HTTPBasicAuthFilter( data.realHttpLogin, data.realHttpPassword );
                }
            }
            if ( meta.isPreemptive() ) {
                data.config.getProperties().put( ApacheHttpClient4Config.PROPERTY_PREEMPTIVE_BASIC_AUTHENTICATION, true );
            }
            // SSL TRUST STORE CONFIGURATION
            if ( !Utils.isEmpty( data.trustStoreFile ) ) {
                try ( FileInputStream trustFileStream = new FileInputStream( data.trustStoreFile ) ) {
                    KeyStore trustStore = KeyStore.getInstance( "JKS" );
                    trustStore.load( trustFileStream, data.trustStorePassword.toCharArray() );
                    TrustManagerFactory tmf = TrustManagerFactory.getInstance( "SunX509" );
                    tmf.init( trustStore );

                    SSLContext ctx = SSLContext.getInstance( "SSL" );
                    ctx.init( null, tmf.getTrustManagers(), null );

                    HostnameVerifier hv = new HostnameVerifier() {
                        public boolean verify( String hostname, SSLSession session ) {
                            if ( isDebug() ) {
                                logDebug( "Warning: URL Host: " + hostname + " vs. " + session.getPeerHost() );
                            }
                            return true;
                        }
                    };
                    data.config.getProperties().put( HTTPSProperties.PROPERTY_HTTPS_PROPERTIES, new HTTPSProperties( hv, ctx ) );
                } catch ( NoSuchAlgorithmException e ) {
                    throw new KettleException( BaseMessages.getString( PKG, "Rest.Error.NoSuchAlgorithm" ), e );
                } catch ( KeyStoreException e ) {
                    throw new KettleException( BaseMessages.getString( PKG, "Rest.Error.KeyStoreException" ), e );
                } catch ( CertificateException e ) {
                    throw new KettleException( BaseMessages.getString( PKG, "Rest.Error.CertificateException" ), e );
                } catch ( FileNotFoundException e ) {
                    throw new KettleException( BaseMessages.getString( PKG, "Rest.Error.FileNotFound", data.trustStoreFile ), e );
                } catch ( IOException e ) {
                    throw new KettleException( BaseMessages.getString( PKG, "Rest.Error.IOException" ), e );
                } catch ( KeyManagementException e ) {
                    throw new KettleException( BaseMessages.getString( PKG, "Rest.Error.KeyManagementException" ), e );
                }
            }
        }
    }

    protected MultivaluedMap<String, String> searchForHeaders( ClientResponse response ) {
        return response.getHeaders();
    }

    @Override
    public boolean processRow( StepMetaInterface smi, StepDataInterface sdi ) throws KettleException {
        if ( first ) {
            first = false;
            meta = ( NtmlclientStepMeta ) smi;
            data = ( NtmlclientStepData ) sdi;
            getFields();
            setUrl();
            setMethod();
            setHeaders();
            setParameters();
            setBodyFields();

            Object[] r = new Object[]{ meta.getUrl() };
            try {
                data.resultParamsvalue = callRest( r );
                putRow( data.outputRowMeta, data.resultParamsvalue.get(0) );
                data.resultParamsvalue.remove(0 );
            } catch ( KettleException e ) {
                String errorMessage;
                if ( getStepMeta().isDoingErrorHandling() ) {
                    errorMessage = e.toString();
                } else {
                    logError( BaseMessages.getString( PKG, "Rest.ErrorInStepRunning" ) + e.getMessage() );
                    setErrors( 1 );
                    logError( Const.getStackTracker( e ) );
                    stopAll();
                    setOutputDone(); // signal end to receiver(s)
                    return false;
                }
                // Simply add this row to the error row
                putError( getInputRowMeta(), r, 1, errorMessage, null, "Rest001" );
            }
            return true;
        } else {
            if ( !data.resultParamsvalue.isEmpty() ) {
                putRow( data.outputRowMeta, data.resultParamsvalue.get(0) );
                data.resultParamsvalue.remove(0 );
                return true;
            } else {
                setOutputDone(); // signal end to receiver(s)
                return false;
            }
        }
    }

    private void getFields() throws KettleStepException {
        if ( Objects.isNull( getInputRowMeta() ) ) {
            data.inputRowMeta = new RowMeta();
            data.outputRowMeta = data.inputRowMeta.clone();
        }
        meta.getFields( data.outputRowMeta, getStepname(), null, null, this, repository, metaStore );
    }

    private void setUrl() throws KettleException {
        if ( meta.isUrlInField() ) {
            if ( Utils.isEmpty( meta.getUrlField() ) ) {
                logError( BaseMessages.getString( PKG, "Rest.Log.NoField" ) );
                throw new KettleException( BaseMessages.getString( PKG, "Rest.Log.NoField" ) );
            }
            // cache the position of the field
            if ( data.indexOfUrlField < 0 ) {
                String realUrlfieldName = environmentSubstitute( meta.getUrlField() );
                data.indexOfUrlField = data.inputRowMeta.indexOfValue( realUrlfieldName );
                if ( data.indexOfUrlField < 0 ) {
                    // The field is unreachable !
                    throw new KettleException( BaseMessages.getString( PKG, "Rest.Exception.ErrorFindingField", realUrlfieldName ) );
                }
            }
        } else {
            // Static URL
            data.realUrl = environmentSubstitute( meta.getUrl() );
        }
    }

    private void setMethod() throws KettleException {
        if ( meta.isDynamicMethod() ) {
            String field = environmentSubstitute( meta.getMethodFieldName() );
            if ( Utils.isEmpty( field ) ) {
                throw new KettleException( BaseMessages.getString( PKG, "Rest.Exception.MethodFieldMissing" ) );
            }
            data.indexOfMethod = data.inputRowMeta.indexOfValue( field );
            if ( data.indexOfMethod < 0 ) {
                // The field is unreachable !
                throw new KettleException( BaseMessages.getString( PKG, "Rest.Exception.ErrorFindingField", field ) );
            }
        }
    }

    private void setHeaders() throws KettleException {
        int nrargs = meta.getHeaderName() == null ? 0 : meta.getHeaderName().length;
        if ( nrargs > 0 ) {
            data.nrheader = nrargs;
            data.indexOfHeaderFields = new int[nrargs];
            data.headerNames = new String[nrargs];
            for ( int i = 0; i < nrargs; i++ ) {
                // split into body / header
                data.headerNames[i] = environmentSubstitute( meta.getHeaderName()[i] );
                String field = environmentSubstitute( meta.getHeaderField()[i] );
                if ( Utils.isEmpty( field ) ) {
                    throw new KettleException( BaseMessages.getString( PKG, "Rest.Exception.HeaderFieldEmpty" ) );
                }
            }
            data.useHeaders = true;
        }
    }

    private void setParameters() throws KettleException {
        if ( NtmlclientStepMeta.isActiveParameters( meta.getMethod() ) ) {
            // Parameters
            int nrparams = meta.getParameterField() == null ? 0 : meta.getParameterField().length;
            if ( nrparams > 0 ) {
                data.nrParams = nrparams;
                data.paramNames = new String[nrparams];
                data.indexOfParamFields = new int[nrparams];
                for ( int i = 0; i < nrparams; i++ ) {
                    data.paramNames[i] = environmentSubstitute( meta.getParameterName()[i] );
                    String field = environmentSubstitute( meta.getParameterField()[i] );
                    if ( Utils.isEmpty( field ) ) {
                        throw new KettleException( BaseMessages.getString( PKG, "Rest.Exception.ParamFieldEmpty" ) );
                    }
                    data.indexOfParamFields[i] = data.inputRowMeta.indexOfValue( field );
                    if ( data.indexOfParamFields[i] < 0 ) {
                        throw new KettleException( BaseMessages.getString( PKG, "Rest.Exception.ErrorFindingField", field ) );
                    }
                }
                data.useParams = true;
            }

            int nrmatrixparams = meta.getMatrixParameterField() == null ? 0 : meta.getMatrixParameterField().length;
            if ( nrmatrixparams > 0 ) {
                data.nrMatrixParams = nrmatrixparams;
                data.matrixParamNames = new String[nrmatrixparams];
                data.indexOfMatrixParamFields = new int[nrmatrixparams];
                for ( int i = 0; i < nrmatrixparams; i++ ) {
                    data.matrixParamNames[i] = environmentSubstitute( meta.getMatrixParameterName()[i] );
                    String field = environmentSubstitute( meta.getMatrixParameterField()[i] );
                    if ( Utils.isEmpty( field ) ) {
                        throw new KettleException( BaseMessages.getString( PKG, "Rest.Exception.MatrixParamFieldEmpty" ) );
                    }
                    data.indexOfMatrixParamFields[i] = data.inputRowMeta.indexOfValue( field );
                    if ( data.indexOfMatrixParamFields[i] < 0 ) {
                        throw new KettleException( BaseMessages.getString( PKG, "Rest.Exception.ErrorFindingField", field ) );
                    }
                }
                data.useMatrixParams = true;
            }
        }
    }

    private void setBodyFields() throws KettleException {
        if ( NtmlclientStepMeta.isActiveBody( meta.getMethod() ) ) {
            String field = environmentSubstitute( meta.getBodyField() );
            if ( !Utils.isEmpty( field ) ) {
                data.indexOfBodyField = data.inputRowMeta.indexOfValue( field );
                if ( data.indexOfBodyField < 0 ) {
                    throw new KettleException( BaseMessages.getString( PKG, "Rest.Exception.ErrorFindingField", field ) );
                }
                data.useBody = true;
            }
        }
    }

    @Override
    public boolean init( StepMetaInterface smi, StepDataInterface sdi ) {
        meta = (NtmlclientStepMeta) smi;
        data = (NtmlclientStepData) sdi;

        if ( super.init( smi, sdi ) ) {
            data.resultFieldName = environmentSubstitute( meta.getFieldName() );
            data.resultCodeFieldName = environmentSubstitute( meta.getResultCodeFieldName() );
            data.resultResponseFieldName = environmentSubstitute( meta.getResponseTimeFieldName() );
            data.resultHeaderFieldName = environmentSubstitute( meta.getResponseHeaderFieldName() );

            // get authentication settings once
            data.realProxyHost = environmentSubstitute( meta.getProxyHost() );
            data.realProxyPort = Const.toInt( environmentSubstitute( meta.getProxyPort() ), 8080 );
            data.realHttpLogin = environmentSubstitute( meta.getHttpLogin() );
            data.realHttpPassword = Encr.decryptPasswordOptionallyEncrypted( environmentSubstitute( meta.getHttpPassword() ) );

            if ( !meta.isDynamicMethod() ) {
                data.method = environmentSubstitute( meta.getMethod() );
                if ( Utils.isEmpty( data.method ) ) {
                    logError( BaseMessages.getString( PKG, "Rest.Error.MethodMissing" ) );
                    return false;
                }
            }

            data.trustStoreFile = environmentSubstitute( meta.getTrustStoreFile() );
            data.trustStorePassword = environmentSubstitute( meta.getTrustStorePassword() );

            String applicationType = Const.NVL( meta.getApplicationType(), "" );
            if ( applicationType.equals( NtmlclientStepMeta.APPLICATION_TYPE_XML ) ) {
                data.mediaType = MediaType.APPLICATION_XML_TYPE;
            } else if ( applicationType.equals( NtmlclientStepMeta.APPLICATION_TYPE_JSON ) ) {
                data.mediaType = MediaType.APPLICATION_JSON_TYPE;
            } else if ( applicationType.equals( NtmlclientStepMeta.APPLICATION_TYPE_OCTET_STREAM ) ) {
                data.mediaType = MediaType.APPLICATION_OCTET_STREAM_TYPE;
            } else if ( applicationType.equals( NtmlclientStepMeta.APPLICATION_TYPE_XHTML ) ) {
                data.mediaType = MediaType.APPLICATION_XHTML_XML_TYPE;
            } else if ( applicationType.equals( NtmlclientStepMeta.APPLICATION_TYPE_FORM_URLENCODED ) ) {
                data.mediaType = MediaType.APPLICATION_FORM_URLENCODED_TYPE;
            } else if ( applicationType.equals( NtmlclientStepMeta.APPLICATION_TYPE_ATOM_XML ) ) {
                data.mediaType = MediaType.APPLICATION_ATOM_XML_TYPE;
            } else if ( applicationType.equals( NtmlclientStepMeta.APPLICATION_TYPE_SVG_XML ) ) {
                data.mediaType = MediaType.APPLICATION_SVG_XML_TYPE;
            } else if ( applicationType.equals( NtmlclientStepMeta.APPLICATION_TYPE_TEXT_XML ) ) {
                data.mediaType = MediaType.TEXT_XML_TYPE;
            } else {
                data.mediaType = MediaType.TEXT_PLAIN_TYPE;
            }
            try {
                setConfig();
            } catch ( Exception e ) {
                logError( BaseMessages.getString( PKG, "Rest.Error.Config" ), e );
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public void dispose( StepMetaInterface smi, StepDataInterface sdi ) {
        meta = (NtmlclientStepMeta) smi;
        data = (NtmlclientStepData) sdi;

        data.config = null;
        data.headerNames = null;
        data.indexOfHeaderFields = null;
        data.paramNames = null;
        super.dispose( smi, sdi );
    }

}
