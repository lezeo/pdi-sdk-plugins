package org.pentaho.di.sdk.samples.steps.ntmlclient;

import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.client.apache4.config.DefaultApacheHttpClient4Config;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.trans.step.BaseStepData;
import org.pentaho.di.trans.step.StepDataInterface;

import javax.ws.rs.core.MediaType;
import java.util.List;

public class NtmlclientStepData extends BaseStepData implements StepDataInterface {
    public RowMetaInterface outputRowMeta;
    public RowMetaInterface inputRowMeta;

    /** URL **/
    public int indexOfUrlField;
    public String realUrl;
    /** Method **/
    public String method;
    /** Index of method **/
    public int indexOfMethod;

    public int nrheader;
    /** Headers **/
    public int[] indexOfHeaderFields;
    public String[] headerNames;

    /** query parameters **/
    public int nrParams;
    public int[] indexOfParamFields;
    public String[] paramNames;

    /** matrix parameters **/
    public int nrMatrixParams;
    public int[] indexOfMatrixParamFields;
    public String[] matrixParamNames;

    public int nrResultParams;
    public String[] resultParamsNames;
    public String[] resultParamsTargetNames;
    public List<Object[]> resultParamsvalue;

    /** proxy **/
    public String realProxyHost;
    public int realProxyPort;
    public String realHttpLogin;
    public String realHttpPassword;

    /** Result fieldnames **/
    public String resultFieldName;
    public String resultCodeFieldName;
    public String resultResponseFieldName;
    public String resultHeaderFieldName;

    /** Flag set headers **/
    public boolean useHeaders;

    /** Flag set Query Parameters **/
    public boolean useParams;

    /** Flag set Matrix Parameters **/
    public boolean useMatrixParams;

    public boolean useResultParams;

    /** Flag set body **/
    public boolean useBody;

    /** Index of body field **/
    public int indexOfBodyField;

    /** trust store **/
    public String trustStoreFile;
    public String trustStorePassword;

    public DefaultApacheHttpClient4Config config;

    public HTTPBasicAuthFilter basicAuthentication;

    public MediaType mediaType;

    public NtmlclientStepData() {
        super();
        this.indexOfUrlField = -1;
        this.realProxyHost = null;
        this.realProxyPort = 8080;
        this.realHttpLogin = null;
        this.realHttpPassword = null;
        this.resultFieldName = null;
        this.resultCodeFieldName = null;
        this.resultResponseFieldName = null;
        this.resultHeaderFieldName = null;
        this.nrheader = 0;
        this.nrParams = 0;
        this.nrMatrixParams = 0;
        this.nrResultParams = 0;
        this.method = null;
        this.indexOfBodyField = -1;
        this.indexOfMethod = -1;
        this.config = null;
        this.trustStoreFile = null;
        this.trustStorePassword = null;
        this.basicAuthentication = null;
    }
}
